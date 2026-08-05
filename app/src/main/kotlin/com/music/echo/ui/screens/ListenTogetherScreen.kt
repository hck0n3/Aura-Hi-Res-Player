

package iad1tya.echo.music.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import iad1tya.echo.music.ui.component.DefaultDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AppBarHeight
import iad1tya.echo.music.constants.ListenTogetherInTopBarKey
import iad1tya.echo.music.constants.ListenTogetherUsernameKey
import iad1tya.echo.music.listentogether.ConnectionState
import iad1tya.echo.music.listentogether.JoinRequestPayload
import iad1tya.echo.music.listentogether.ListenTogetherEvent
import iad1tya.echo.music.listentogether.SuggestionReceivedPayload
import iad1tya.echo.music.listentogether.UserInfo
import iad1tya.echo.music.ui.component.ListDialog
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanel
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraSpacing
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListenTogetherScreen(
    navController: NavController,
    showTopBar: Boolean = false
) {
    val context = LocalContext.current
    val listenTogetherManager = LocalListenTogetherManager.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    if (listenTogetherManager == null) {
        NotConfiguredContent()
        return
    }

    val connectionState by listenTogetherManager.connectionState.collectAsState()
    val roomState by listenTogetherManager.roomState.collectAsState()
    val userId by listenTogetherManager.userId.collectAsState()
    val pendingJoinRequests by listenTogetherManager.pendingJoinRequests.collectAsState()
    val pendingSuggestions by listenTogetherManager.pendingSuggestions.collectAsState()

    val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
    val shouldShowTopBar = showTopBar || listenTogetherInTopBar
    
    var savedUsername by rememberPreference(ListenTogetherUsernameKey, "")
    var roomCodeInput by rememberSaveable { mutableStateOf("") }
    var usernameInput by rememberSaveable { mutableStateOf(savedUsername) }

    var isCreatingRoom by rememberSaveable { mutableStateOf(false) }
    var isJoiningRoom by rememberSaveable { mutableStateOf(false) }
    var joinErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    var selectedUserForMenu by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUsername by rememberSaveable { mutableStateOf<String?>(null) }

    val waitingForApprovalText = stringResource(R.string.waiting_for_approval)
    val invalidRoomCodeText = stringResource(R.string.invalid_room_code)
    val joinRequestDeniedText = stringResource(R.string.join_request_denied)
    val connectionFailedText = stringResource(R.string.listen_together_connection_failed)
    val kickedFromRoomText = stringResource(R.string.listen_together_kicked_from_room)

    LaunchedEffect(savedUsername) {
        if (usernameInput.isBlank() && savedUsername.isNotBlank()) {
            usernameInput = savedUsername
        }
    }

    LaunchedEffect(listenTogetherManager) {
        listenTogetherManager.events.collect { event ->
            when (event) {
                is ListenTogetherEvent.JoinRejected -> {
                    val reason = event.reason
                    joinErrorMessage = when {
                        reason.isNullOrBlank() -> joinRequestDeniedText
                        reason.contains("invalid", ignoreCase = true) -> invalidRoomCodeText
                        else -> "$joinRequestDeniedText: $reason"
                    }
                    isJoiningRoom = false
                    isCreatingRoom = false
                }
                is ListenTogetherEvent.JoinApproved -> {
                    isJoiningRoom = false
                    joinErrorMessage = null
                }
                is ListenTogetherEvent.RoomCreated -> {
                    isCreatingRoom = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("ListenTogetherRoom", event.roomCode)
                    clipboard.setPrimaryClip(clip)
                }
                // Without these branches the client could retry up to 15 times / 120s and then emit a
                // failure nobody read, leaving isJoiningRoom stuck true and the spinner running forever.
                is ListenTogetherEvent.ConnectionError -> {
                    joinErrorMessage = connectionFailedText
                    isJoiningRoom = false
                    isCreatingRoom = false
                }
                is ListenTogetherEvent.Kicked -> {
                    joinErrorMessage = if (event.reason.isBlank()) {
                        kickedFromRoomText
                    } else {
                        "$kickedFromRoomText: ${event.reason}"
                    }
                    isJoiningRoom = false
                    isCreatingRoom = false
                }
                is ListenTogetherEvent.ServerError -> {
                    // Only while a join/create is in flight: in-room server errors are handled elsewhere.
                    if (isJoiningRoom || isCreatingRoom) {
                        joinErrorMessage = event.message.ifBlank { connectionFailedText }
                        isJoiningRoom = false
                        isCreatingRoom = false
                    }
                }
                is ListenTogetherEvent.Disconnected -> {
                    // Disconnected also fires on a normal leave, so it is only an error when the user is
                    // still waiting on a join/create — otherwise leaving a room would flash a fake error.
                    if (isJoiningRoom || isCreatingRoom) {
                        joinErrorMessage = connectionFailedText
                        isJoiningRoom = false
                        isCreatingRoom = false
                    }
                }
                else -> {}
            }
        }
    }

    val isInRoom = listenTogetherManager.isInRoom
    val isHost = roomState?.hostId == userId

    
    if (selectedUserForMenu != null && selectedUsername != null) {
        UserActionDialog(
            username = selectedUsername ?: "",
            onKick = {
                selectedUserForMenu?.let {
                    listenTogetherManager.kickUser(it, "Removed by host")
                }
                selectedUserForMenu = null
                selectedUsername = null
            },
            onPermanentKick = {
                selectedUserForMenu?.let { userId ->
                    selectedUsername?.let { username ->
                        listenTogetherManager.blockUser(username)
                        listenTogetherManager.kickUser(userId, R.string.user_blocked_by_host.toString())
                    }
                }
                selectedUserForMenu = null
                selectedUsername = null
            },
            onTransferOwnership = {
                selectedUserForMenu?.let {
                    listenTogetherManager.transferHost(it)
                }
                selectedUserForMenu = null
                selectedUsername = null
            },
            onDismiss = {
                selectedUserForMenu = null
                selectedUsername = null
            }
        )
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    
    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // ONE flag read for the whole screen; every panel below takes it as a parameter. The Listen
    // Together PROTOCOL and its state machine are untouched — this is chrome only.
    val skin = rememberAuraPanelSkin()
    val auraDark = skin.enabled && skin.darkGround

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .background(if (auraDark) AuraPalette.Ground else MaterialTheme.colorScheme.background)
            .imePadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = windowInsets.asPaddingValues().calculateTopPadding() + 16.dp,
            bottom = windowInsets.asPaddingValues().calculateBottomPadding() + 16.dp + AppBarHeight
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (connectionState == ConnectionState.CONNECTED && !isInRoom) {
            item {
                Text(
                    text = stringResource(R.string.listen_together_background_disconnect_note),
                    style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
                    color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (isInRoom) {
            
            roomState?.let { room ->
                item {
                    RoomStatusCard(
                        roomCode = room.roomCode,
                        isHost = isHost,
                        skin = skin,
                        context = context,
                        navController = navController
                    )
                }

                
                val connectedUsers = room.users.filter { it.isConnected }
                val currentUserIdValue = userId ?: ""
                item {
                    ConnectedUsersSection(
                        users = connectedUsers,
                        isHost = isHost,
                        skin = skin,
                        currentUserId = currentUserIdValue,
                        onUserClick = { clickedUserId, username ->
                            if (isHost && clickedUserId != currentUserIdValue) {
                                selectedUserForMenu = clickedUserId
                                selectedUsername = username
                            }
                        }
                    )
                }

                
                if (isHost && pendingJoinRequests.isNotEmpty()) {
                    item {
                        PendingJoinRequestsSection(
                            requests = pendingJoinRequests,
                            skin = skin,
                            onApprove = { listenTogetherManager.approveJoin(it) },
                            onReject = { listenTogetherManager.rejectJoin(it, "Rejected by host") }
                        )
                    }
                }

                
                if (isHost && pendingSuggestions.isNotEmpty()) {
                    item {
                        PendingSuggestionsSection(
                            suggestions = pendingSuggestions,
                            skin = skin,
                            onApprove = { listenTogetherManager.approveSuggestion(it) },
                            onReject = { listenTogetherManager.rejectSuggestion(it, "Rejected by host") }
                        )
                    }
                }

                
                item {
                    Button(
                        onClick = { listenTogetherManager.leaveRoom() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            // Stays the theme's error colour: leaving a room is a destructive action
                            // and its colour is the warning.
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.logout),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.leave_room),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            
            item {
                JoinCreateRoomSection(
                    skin = skin,
                    usernameInput = usernameInput,
                    onUsernameChange = { usernameInput = it },
                    roomCodeInput = roomCodeInput,
                    onRoomCodeChange = { roomCodeInput = it },
                    savedUsername = savedUsername,
                    isJoiningRoom = isJoiningRoom,
                    joinErrorMessage = joinErrorMessage,
                    waitingForApprovalText = waitingForApprovalText,
                    bringIntoViewRequester = bringIntoViewRequester,
                    onCreateRoom = {
                        val username = usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                        val finalUsername = username.trim()
                        if (finalUsername.isNotBlank()) {
                            savedUsername = finalUsername
                            Toast.makeText(context, R.string.creating_room, Toast.LENGTH_SHORT).show()
                            isCreatingRoom = true
                            isJoiningRoom = false
                            joinErrorMessage = null
                            listenTogetherManager.connect()
                            listenTogetherManager.createRoom(finalUsername)
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onJoinRoom = {
                        val username = usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                        val finalUsername = username.trim()
                        if (finalUsername.isNotBlank()) {
                            savedUsername = finalUsername
                            Toast.makeText(
                                context,
                                context.getString(R.string.joining_room, roomCodeInput),
                                Toast.LENGTH_SHORT
                            ).show()
                            isJoiningRoom = true
                            isCreatingRoom = false
                            joinErrorMessage = null
                            listenTogetherManager.connect()
                            listenTogetherManager.joinRoom(roomCodeInput, finalUsername)
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFieldFocused = {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                )
            }
        }

        
        item {
            SettingsLinkCard(
                onClick = { navController.navigate("settings/integrations/listen_together") }
            )
        }
        
        if (!isInRoom) {
            item {
                AuraPanel(
                    skin = skin,
                    modifier = Modifier.fillMaxWidth(),
                    classicShape = RoundedCornerShape(24.dp),
                    classicColors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "How it Works",
                            style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleLarge,
                            fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
                            color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.primary
                        )

                        InstructionStep(
                            title = "1. Create a Room",
                            description = "Start a session and share the unique room code with your friends.",
                            skin = skin
                        )

                        InstructionStep(
                            title = "2. Join a Friend",
                            description = "Enter their room code to join their session instantly.",
                            skin = skin
                        )

                        InstructionStep(
                            title = "3. Sync Playback",
                            description = "The host controls the music. Everyone listens in perfect sync!",
                            skin = skin
                        )
                    }
                }
            }
        }
    }

    if (shouldShowTopBar) {
        TopAppBar(
            title = { Text(stringResource(R.string.listen_together)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = null
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            actions = {
                if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                    TextButton(onClick = { listenTogetherManager.connect() }) {
                        Text(stringResource(R.string.connect))
                    }
                } else if (connectionState == ConnectionState.CONNECTED) {
                    TextButton(onClick = { listenTogetherManager.disconnect() }) {
                        Text(stringResource(R.string.disconnect))
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                        color = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = if (auraDark) {
                androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = AuraPalette.Ground,
                    titleContentColor = skin.ink,
                    navigationIconContentColor = skin.ink,
                    actionIconContentColor = skin.ink,
                )
            } else {
                // The `TopAppBar` default, spelled out so the classic bar is unchanged.
                androidx.compose.material3.TopAppBarDefaults.topAppBarColors()
            }
        )
    }
}

@Composable
private fun InstructionStep(title: String, description: String, skin: AuraPanelSkin) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.titleMedium,
            fontWeight = if (skin.enabled) FontWeight.SemiBold else FontWeight.Bold,
            color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
            // `RowSubtitle` carries its own 18 sp leading; the classic 20 sp override only applies to
            // the `bodyMedium` it replaces.
            lineHeight = if (skin.enabled) TextUnit.Unspecified else 20.sp
        )
    }
}

@Composable
private fun NotConfiguredContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.group),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.listen_together),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.listen_together_not_configured),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            color = when (connectionState) {
                                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiary
                                ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                                ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> stringResource(R.string.listen_together_connected)
                        ConnectionState.CONNECTING -> stringResource(R.string.listen_together_connecting)
                        ConnectionState.RECONNECTING -> stringResource(R.string.listen_together_reconnecting)
                        ConnectionState.ERROR -> stringResource(R.string.listen_together_error)
                        ConnectionState.DISCONNECTED -> stringResource(R.string.listen_together_disconnected)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (connectionState) {
                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiary
                        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.connect), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(R.string.disconnect), fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(
                        onClick = onReconnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reconectar", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomStatusCard(
    roomCode: String,
    isHost: Boolean,
    skin: AuraPanelSkin,
    context: Context,
    navController: NavController
) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.room_code),
                style = if (skin.enabled) AuraType.SectionLabel else MaterialTheme.typography.labelLarge,
                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = roomCode,
                // The code is the one thing on this card the user has to read out loud, so under the
                // new skin it becomes what the render uses for technical data: tracked monospace, at
                // display size. The existing 6 sp tracking is kept on both paths.
                style = if (skin.enabled)
                    AuraType.Technical.copy(fontSize = 34.sp, lineHeight = 40.sp)
                else
                    MaterialTheme.typography.displaySmall,
                color = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isHost)
                    stringResource(R.string.listen_together_you_are_host)
                else
                    stringResource(R.string.listen_together_you_are_guest),
                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { navController.navigate("listen_together/chat") },
                shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (skin.enabled) skin.accent.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.chat_msg),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.comments),
                    fontWeight = FontWeight.Bold
                )
            }

            if (isHost) {
                Spacer(modifier = Modifier.height(16.dp))
                // echomusic://listen?code=… is the scheme this app actually registers (AndroidManifest)
                // and parses (MainActivity.handleDeepLink: host "listen" + "code" query parameter). The
                // previous https://echomusic-listen-together.onrender.com host belongs to an upstream
                // fork we do not control, so its App Link could never verify and every invite 404'd.
                val inviteLink = remember(roomCode) { "echomusic://listen?code=$roomCode" }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(12.dp),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            // The BARE link: this button is labelled "copy link", so pasting it into a URL
                            // field must work. (The friendly invite sentence belongs to a share action.)
                            val clip = android.content.ClipData.newPlainText("Listen Together Link", inviteLink)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = stringResource(R.string.copy_link),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.copy_link))
                    }

                    FilledTonalButton(
                        shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(12.dp),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Room Code", roomCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.content_copy),
                            contentDescription = stringResource(R.string.copy_code),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.copy_code))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedUsersSection(
    users: List<UserInfo>,
    isHost: Boolean,
    skin: AuraPanelSkin,
    currentUserId: String,
    onUserClick: (String, String) -> Unit
) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "${stringResource(R.string.connected_users)} (${users.size})",
                style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleMedium,
                fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                users.forEach { user ->
                    UserAvatar(
                        user = user,
                        isCurrentUser = user.userId == currentUserId,
                        isClickable = isHost && user.userId != currentUserId,
                        skin = skin,
                        onClick = { onUserClick(user.userId, user.username) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserAvatar(
    user: UserInfo,
    isCurrentUser: Boolean,
    isClickable: Boolean,
    skin: AuraPanelSkin,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(enabled = isClickable, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                // Host = teal, you = blue, everyone else = the card's own wash. Three ROLES, three
                // stops of the render's own ramp, instead of three unrelated Material containers.
                color = when {
                    user.isHost -> if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
                    isCurrentUser -> if (skin.enabled && skin.darkGround) AuraPalette.Blue
                    else MaterialTheme.colorScheme.secondary
                    else -> if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = user.username.take(1).uppercase(),
                        style = if (skin.enabled) AuraType.PlayerTitle else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            user.isHost -> if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                            else MaterialTheme.colorScheme.onPrimary
                            isCurrentUser -> if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                            else MaterialTheme.colorScheme.onSecondary
                            else -> if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (user.isHost || isCurrentUser) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(20.dp),
                    shape = CircleShape,
                    color = if (user.isHost) {
                        if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
                    } else {
                        if (skin.enabled && skin.darkGround) AuraPalette.Blue
                        else MaterialTheme.colorScheme.secondary
                    }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = painterResource(
                                if (user.isHost) R.drawable.crown else R.drawable.person
                            ),
                            contentDescription = null,
                            tint = if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                            else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = user.username,
            style = if (skin.enabled) AuraType.MiniTitle else MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Medium,
            color = if (user.isHost) {
                if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
            } else {
                if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (user.isHost) {
            Text(
                text = stringResource(R.string.host_label),
                style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelSmall,
                color = if (skin.enabled) skin.accent.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        } else if (isCurrentUser) {
            Text(
                text = stringResource(R.string.you_label),
                style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelSmall,
                color = if (skin.enabled && skin.darkGround) AuraPalette.Blue.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun PendingJoinRequestsSection(
    requests: List<JoinRequestPayload>,
    skin: AuraPanelSkin,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.listen_together_join_requests),
                style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleMedium,
                fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            requests.forEach { request ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Identity on the classic path; asserts the touch minimum on the new one.
                        .then(if (skin.enabled) Modifier.sizeIn(minHeight = AuraSpacing.MinTouchTarget) else Modifier)
                        .padding(vertical = 8.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = if (skin.enabled && skin.darkGround) AuraPalette.Blue
                        else MaterialTheme.colorScheme.secondary
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = request.username.take(1).uppercase(),
                                style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                                else MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = request.username,
                        style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (skin.enabled) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (skin.enabled) skin.ink else Color.Unspecified,
                        modifier = Modifier.weight(1f)
                    )
                    MaterialIconButton(onClick = { onApprove(request.userId) }) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.approve),
                            tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    MaterialIconButton(onClick = { onReject(request.userId) }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.reject),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSuggestionsSection(
    suggestions: List<SuggestionReceivedPayload>,
    skin: AuraPanelSkin,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.pending_suggestions),
                style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleMedium,
                fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (skin.enabled) Modifier.sizeIn(minHeight = AuraSpacing.MinTouchTarget) else Modifier)
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.trackInfo.title,
                            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyMedium,
                            fontWeight = if (skin.enabled) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (skin.enabled) skin.ink else Color.Unspecified,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = suggestion.fromUsername,
                            style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    MaterialIconButton(onClick = { onApprove(suggestion.suggestionId) }) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.approve),
                            tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    MaterialIconButton(onClick = { onReject(suggestion.suggestionId) }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.reject),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinCreateRoomSection(
    skin: AuraPanelSkin,
    usernameInput: String,
    onUsernameChange: (String) -> Unit,
    roomCodeInput: String,
    onRoomCodeChange: (String) -> Unit,
    savedUsername: String,
    isJoiningRoom: Boolean,
    joinErrorMessage: String?,
    waitingForApprovalText: String,
    bringIntoViewRequester: BringIntoViewRequester,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onFieldFocused: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Crear / Unirse: a two-way segmented control, which the render draws as a pill track.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp))
                    .background(
                        if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Button(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                    shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 0) {
                            if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
                        } else Color.Transparent,
                        contentColor = if (selectedTab == 0) {
                            if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                            else MaterialTheme.colorScheme.onPrimary
                        } else {
                            if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.create_room), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                    shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        // The second stop of the render's ramp, so the two tabs read as one control
                        // rather than two unrelated Material roles.
                        containerColor = if (selectedTab == 1) {
                            if (skin.enabled && skin.darkGround) AuraPalette.Blue
                            else MaterialTheme.colorScheme.tertiary
                        } else Color.Transparent,
                        contentColor = if (selectedTab == 1) {
                            if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                            else MaterialTheme.colorScheme.onTertiary
                        } else {
                            if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.login),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.join_room), fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedTextField(
                value = usernameInput,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.username)) },
                placeholder = { Text(stringResource(R.string.enter_username)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.person),
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (usernameInput.isNotBlank()) {
                        MaterialIconButton(onClick = { onUsernameChange("") }) {
                            Icon(painterResource(R.drawable.close), null)
                        }
                    }
                },
                singleLine = true,
                shape = if (skin.enabled) AuraShapes.Card else RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (skin.enabled) skin.line
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFieldFocused() }
            )

            AnimatedVisibility(
                visible = selectedTab == 1,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                OutlinedTextField(
                    value = roomCodeInput,
                    onValueChange = { if (it.length <= 8) onRoomCodeChange(it.uppercase()) },
                    label = { Text(stringResource(R.string.room_code)) },
                    placeholder = { Text(stringResource(R.string.enter_room_code)) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.group),
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (roomCodeInput.isNotBlank()) {
                            MaterialIconButton(onClick = { onRoomCodeChange("") }) {
                                Icon(painterResource(R.drawable.close), null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = if (skin.enabled) AuraShapes.Card else RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (skin.enabled) skin.line
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged { if (it.isFocused) onFieldFocused() }
                )
            }

            AnimatedVisibility(
                visible = isJoiningRoom,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = if (skin.enabled) AuraShapes.Card else RoundedCornerShape(12.dp),
                    color = if (skin.enabled) skin.accent.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = waitingForApprovalText,
                            style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
                            color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = joinErrorMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                // The error banner keeps `errorContainer` on both paths: its colour is the message.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = if (skin.enabled) AuraShapes.Card else RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.error),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = joinErrorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            val hasUsername = usernameInput.trim().isNotBlank() || savedUsername.isNotBlank()
            val hasRoomCode = roomCodeInput.length == 8

            if (selectedTab == 0) {
                Button(
                    onClick = onCreateRoom,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasUsername,
                    shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                        contentColor = if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                        else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.create_room), fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onJoinRoom,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasUsername && hasRoomCode,
                    shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (skin.enabled && skin.darkGround) AuraPalette.Blue
                        else MaterialTheme.colorScheme.tertiary,
                        contentColor = if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                        else MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.login),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.join_room), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettingsLinkCard(onClick: () -> Unit) {
    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.settings),
                title = { Text(stringResource(R.string.settings)) },
                description = { Text(stringResource(R.string.listen_together_settings_desc)) },
                onClick = onClick
            )
        )
    )
}

@Composable
private fun UserActionDialog(
    username: String,
    onKick: () -> Unit,
    onPermanentKick: () -> Unit,
    onTransferOwnership: () -> Unit,
    onDismiss: () -> Unit
) {
    DefaultDialog(
        onDismiss = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.group),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.manage_user),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onKick),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.kick_user),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.kick_user_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPermanentKick),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.permanently_kick_user),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.permanently_kick_user_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTransferOwnership),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.crown),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.transfer_ownership),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.transfer_ownership_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
