

package iad1tya.echo.music.ui.player

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AutoLoadMoreKey
import iad1tya.echo.music.constants.ListItemHeight
import iad1tya.echo.music.constants.PlayerBackgroundStyle
import iad1tya.echo.music.constants.QueueEditLockKey
import iad1tya.echo.music.constants.ShowCommentButtonKey
import iad1tya.echo.music.constants.UseNewPlayerDesignKey
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.move
import iad1tya.echo.music.extensions.toggleRepeatMode
import iad1tya.echo.music.listentogether.RoomRole
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.ui.component.ActionPromptDialog
import iad1tya.echo.music.ui.component.BottomSheet
import iad1tya.echo.music.ui.component.BottomSheetState
import iad1tya.echo.music.ui.component.ChipsRow
import iad1tya.echo.music.ui.component.LocalBottomSheetPageState
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.MediaMetadataListItem
import iad1tya.echo.music.ui.menu.PlayerMenu
import iad1tya.echo.music.ui.menu.QueueMenu
import iad1tya.echo.music.ui.menu.SelectionMediaMetadataMenu
import iad1tya.echo.music.ui.screens.CommentSheet
import iad1tya.echo.music.ui.utils.ShowMediaInfo
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.utils.listItemShape
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberPreference
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.produceState
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import iad1tya.echo.music.echomusic.isBluetoothHeadphoneConnected
import iad1tya.echo.music.echomusic.AudioDeviceBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.widget.Toast
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Queue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    background: Color,
    onBackgroundColor: Color,
    TextBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    pureBlack: Boolean,
    showInlineLyrics: Boolean,
    playerBackground: PlayerBackgroundStyle = PlayerBackgroundStyle.DEFAULT,
    onToggleLyrics: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboard.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    var showAudioDeviceBottomSheet by remember { mutableStateOf(false) }

    val isBluetoothConnected by produceState(initialValue = isBluetoothHeadphoneConnected(context)) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                value = isBluetoothHeadphoneConnected(context)
            }
        }

        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    value = isBluetoothHeadphoneConnected(context)
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    value = isBluetoothHeadphoneConnected(context)
                }
            }
        } else null

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("android.media.AUDIO_BECOMING_NOISY")
        }
        
        context.registerReceiver(receiver, filter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }

        awaitDispose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                audioManager.unregisterAudioDeviceCallback(callback)
            }
        }
    }

    
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = iad1tya.echo.music.listentogether.RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()

    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()

    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)

    val selectedSongs = remember { mutableStateListOf<MediaMetadata>() }
    val selectedItems = remember { mutableStateListOf<Timeline.Window>() }

    
    val castHandler = remember(playerConnection) {
        try {
            playerConnection.service.castConnectionHandler
        } catch (e: Exception) {
            null
        }
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsState() ?: remember { mutableStateOf(false) }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    var locked by rememberPreference(QueueEditLockKey, defaultValue = false)

    // ── Queue sheet tabs (YT Music parity): 0 = UP NEXT (queue, default), 1 = LYRICS, 2 = RELATED.
    // Hoisted OUTSIDE the BottomSheet content (which is not composed while collapsed) so the
    // collapse-reset below always runs: reopening the sheet lands on UP NEXT, and the lyrics
    // ticker/related list never stay alive in a hidden sheet (heat/battery).
    var selectedTab by rememberSaveable { mutableIntStateOf(QUEUE_TAB_NEXT) }
    LaunchedEffect(state.isCollapsed) {
        if (state.isCollapsed) selectedTab = QUEUE_TAB_NEXT
    }

    val (useNewPlayerDesign, onUseNewPlayerDesignChange) = rememberPreference(
        UseNewPlayerDesignKey,
        defaultValue = true
    )
    val (showCommentButton) = rememberPreference(
        ShowCommentButtonKey,
        defaultValue = false
    )

    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerValue by remember { mutableFloatStateOf(30f) }
    val sleepTimerEnabled = remember(
        playerConnection.service.sleepTimer.triggerTime,
        playerConnection.service.sleepTimer.pauseWhenSongEnd
    ) {
        playerConnection.service.sleepTimer.isActive
    }
    var sleepTimerTimeLeft by remember { mutableLongStateOf(0L) }
    var showCommentSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft = if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                    playerConnection.player.duration - playerConnection.player.currentPosition
                } else {
                    playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                }
                delay(1000L)
            }
        }
    }

    BottomSheet(
        state = state,
        modifier = modifier,
        background = {
            Box(Modifier.fillMaxSize().background(Color.Unspecified))
        },
        collapsedContent = {
            if (useNewPlayerDesign) {
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp, vertical = 12.dp)
                        .windowInsetsPadding(
                            WindowInsets.systemBars.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            ),
                        ),
                ) {
                    val buttonSize = 42.dp
                    val iconSize = 24.dp
                    val queueShape = RoundedCornerShape(
                        topStart = 50.dp, bottomStart = 50.dp,
                        topEnd = 3.dp, bottomEnd = 3.dp
                    )
                    val middleShape = RoundedCornerShape(3.dp)
                    val repeatShape = RoundedCornerShape(
                        topStart = 3.dp, bottomStart = 3.dp,
                        topEnd = 50.dp, bottomEnd = 50.dp
                    )



                    PlayerQueueButton(
                        icon = R.drawable.queue_music,
                        onClick = { state.expandSoft() },
                        isActive = false,
                        shape = queueShape,
                        modifier = Modifier.size(buttonSize),
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        iconSize = iconSize,
                        textBackgroundColor = TextBackgroundColor,
                        playerBackground = playerBackground
                    )

                    PlayerQueueButton(
                        icon = R.drawable.bedtime,
                        onClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        isActive = sleepTimerEnabled,
                        enabled = !isListenTogetherGuest,
                        shape = middleShape,
                        modifier = Modifier.size(buttonSize),
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        text = if (sleepTimerEnabled) makeTimeString(sleepTimerTimeLeft) else null,
                        iconSize = iconSize,
                        textBackgroundColor = TextBackgroundColor,
                        playerBackground = playerBackground
                    )

                    PlayerQueueButton(
                        icon = R.drawable.lyrics,
                        onClick = { onToggleLyrics() },
                        isActive = showInlineLyrics,
                        shape = middleShape,
                        modifier = Modifier.size(buttonSize),
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        iconSize = iconSize,
                        textBackgroundColor = TextBackgroundColor,
                        playerBackground = playerBackground
                    )

                    if (showCommentButton) {
                        PlayerQueueButton(
                            icon = R.drawable.chat_msg,
                            onClick = { showCommentSheet = true },
                            isActive = showCommentSheet,
                            shape = middleShape,
                            modifier = Modifier.size(buttonSize),
                            textButtonColor = textButtonColor,
                            iconButtonColor = iconButtonColor,
                            iconSize = iconSize,
                            textBackgroundColor = TextBackgroundColor,
                            playerBackground = playerBackground
                        )
                    }

                    val shuffleModeEnabledInside by playerConnection.shuffleModeEnabled.collectAsState()
                    PlayerQueueButton(
                        icon = R.drawable.shuffle,
                        onClick = {
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabledInside
                        },
                        isActive = shuffleModeEnabledInside,
                        enabled = !isListenTogetherGuest,
                        shape = middleShape,
                        modifier = Modifier.size(buttonSize),
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        iconSize = iconSize,
                        textBackgroundColor = TextBackgroundColor,
                        playerBackground = playerBackground
                    )


                    PlayerQueueButton(
                        icon = when (repeatMode) {
                            Player.REPEAT_MODE_ALL -> R.drawable.repeat
                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                            else -> R.drawable.repeat
                        },
                        onClick = {
                            playerConnection.player.toggleRepeatMode()
                        },
                        isActive = repeatMode != Player.REPEAT_MODE_OFF,
                        enabled = !isListenTogetherGuest,
                        shape = repeatShape,
                        modifier = Modifier.size(buttonSize),
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        iconSize = iconSize,
                        textBackgroundColor = TextBackgroundColor,
                        playerBackground = playerBackground
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .size(buttonSize)
                            .clip(CircleShape)
                            .background(textButtonColor)
                            .clickable {
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = mediaMetadata,
                                        navController = navController,
                                        playerBottomSheetState = playerBottomSheetState,
                                        onShowDetailsDialog = {
                                            mediaMetadata?.id?.let {
                                                bottomSheetPageState.show {
                                                    ShowMediaInfo(it)
                                                }
                                            }
                                        },
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.more_vert),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = iconButtonColor
                        )
                    }
                }
            } else {
                
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp, vertical = 12.dp)
                        .windowInsetsPadding(
                            WindowInsets.systemBars
                                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        ),
                ) {
                    TextButton(
                        onClick = { state.expandSoft() },
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.apple_queue),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = TextBackgroundColor
                            )









                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.width(120.dp)
                    ) {
                        ToggleButton(
                            checked = false,
                            onCheckedChange = {
                                showAudioDeviceBottomSheet = true
                            },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            modifier = Modifier
                                .height(56.dp)
                                .weight(1f),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = TextBackgroundColor.copy(alpha = 0.2f),
                                contentColor = TextBackgroundColor,
                                checkedContainerColor = TextBackgroundColor.copy(alpha = 0.4f),
                                checkedContentColor = TextBackgroundColor
                            )
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isBluetoothConnected) R.drawable.headset_applemusic else R.drawable.speaker_apple
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        ToggleButton(
                            checked = sleepTimerEnabled,
                            onCheckedChange = {
                                if (!isListenTogetherGuest) {
                                    if (sleepTimerEnabled) {
                                        playerConnection.service.sleepTimer.clear()
                                    } else {
                                        showSleepTimerDialog = true
                                    }
                                }
                            },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            modifier = Modifier
                                .height(56.dp)
                                .weight(1f),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = TextBackgroundColor.copy(alpha = 0.2f),
                                contentColor = TextBackgroundColor,
                                checkedContainerColor = TextBackgroundColor.copy(alpha = 0.4f),
                                checkedContentColor = TextBackgroundColor
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.sleep_timer),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp)
                                )
                                if (sleepTimerEnabled) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = makeTimeString(sleepTimerTimeLeft),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextBackgroundColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            onToggleLyrics()
                        },
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.apple_music_me),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = TextBackgroundColor
                            )









                        }
                    }
                }
            }
            if (showAudioDeviceBottomSheet) {
                AudioDeviceBottomSheet(onDismiss = { showAudioDeviceBottomSheet = false })
            }

            if (showSleepTimerDialog) {
                ActionPromptDialog(
                    titleBar = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.sleep_timer),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    },
                    onDismiss = { showSleepTimerDialog = false },
                    onConfirm = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                    },
                    onCancel = {
                        showSleepTimerDialog = false
                    },
                    onReset = {
                        sleepTimerValue = 30f 
                    },
                    content = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.minute,
                                    sleepTimerValue.roundToInt(),
                                    sleepTimerValue.roundToInt()
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )

                            Spacer(Modifier.height(16.dp))

                            Slider(
                                value = sleepTimerValue,
                                onValueChange = { sleepTimerValue = it },
                                valueRange = 5f..120f,
                                steps = (120 - 5) / 5 - 1,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    showSleepTimerDialog = false
                                    playerConnection.service.sleepTimer.start(-1)
                                }
                            ) {
                                Text(stringResource(R.string.end_of_song))
                            }
                        }
                    }
                )
            }
        },
    ) {
        val queueTitle by playerConnection.queueTitle.collectAsState()
        val queueWindows by playerConnection.queueWindows.collectAsState()
        val automix by playerConnection.service.automixItems.collectAsState()
        // Autoplay footer state: chips come from the service via PlayerConnection (no network in the UI
        // layer); the toggle is the SAME pref as Settings → "Auto load more songs", so both stay in sync.
        val autoplayChips by playerConnection.autoplayChips.collectAsState()
        val autoplaySelectedChip by playerConnection.autoplaySelectedChip.collectAsState()
        val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(AutoLoadMoreKey, defaultValue = true)
        val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
        val queueLength =
            remember(queueWindows) {
                queueWindows.sumOf { it.mediaItem.metadata!!.duration }
            }

        val coroutineScope = rememberCoroutineScope()

        val headerItems = 1
        val lazyListState = rememberLazyListState()
        var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        val currentPlayingUid = remember(currentWindowIndex, queueWindows) {
            if (currentWindowIndex in queueWindows.indices) {
                queueWindows[currentWindowIndex].uid
            } else null
        }

        val reorderableState = rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = WindowInsets.systemBars.add(
                WindowInsets(
                    top = ListItemHeight,
                    bottom = ListItemHeight
                )
            ).asPaddingValues()
        ) { from, to ->
            val currentDragInfo = dragInfo
            dragInfo = if (currentDragInfo == null) {
                from.index to to.index
            } else {
                currentDragInfo.first to to.index
            }

            val safeFrom = (from.index - headerItems).coerceIn(0, mutableQueueWindows.lastIndex)
            val safeTo = (to.index - headerItems).coerceIn(0, mutableQueueWindows.lastIndex)

            mutableQueueWindows.move(safeFrom, safeTo)
        }

        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging) {
                dragInfo?.let { (from, to) ->
                    val safeFrom = (from - headerItems).coerceIn(0, queueWindows.lastIndex)
                    val safeTo = (to - headerItems).coerceIn(0, queueWindows.lastIndex)

                    if (!playerConnection.player.shuffleModeEnabled) {
                        playerConnection.player.moveMediaItem(safeFrom, safeTo)
                    } else {
                        playerConnection.player.setShuffleOrder(
                            DefaultShuffleOrder(
                                queueWindows.map { it.firstPeriodIndex }
                                    .toMutableList()
                                    .move(safeFrom, safeTo)
                                    .toIntArray(),
                                System.currentTimeMillis()
                            )
                        )
                    }
                    dragInfo = null
                }
            }
        }

        LaunchedEffect(queueWindows) {
            mutableQueueWindows.apply {
                clear()
                addAll(queueWindows)
            }
        }

        LaunchedEffect(mutableQueueWindows) {
            if (currentWindowIndex != -1) {
                lazyListState.scrollToItem(currentWindowIndex)
            }
        }

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .background(background),
        ) {
            Column(
                modifier =
                Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { }
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaMetadata?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val likeDescription = if (currentSong?.song?.liked == true) stringResource(R.string.action_remove_like) else stringResource(R.string.action_like)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(likeDescription) } },
                        state = rememberTooltipState(),
                    ) {
                        FilledTonalIconButton(
                            onClick = playerConnection::toggleLike,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (currentSong?.song?.liked == true) R.drawable.favorite
                                    else R.drawable.favorite_border
                                ),
                                contentDescription = likeDescription,
                                tint = if (currentSong?.song?.liked == true) MaterialTheme.colorScheme.error else LocalContentColor.current
                            )
                        }
                    }

                    val lockDescription = if (locked) stringResource(R.string.unlock_queue) else stringResource(R.string.lock_queue)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(lockDescription) } },
                        state = rememberTooltipState(),
                    ) {
                        FilledTonalIconButton(
                            onClick = { locked = !locked },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                contentDescription = lockDescription,
                            )
                        }
                    }

                    val moreDescription = stringResource(R.string.more_options)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(moreDescription) } },
                        state = rememberTooltipState(),
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = mediaMetadata,
                                        navController = navController,
                                        playerBottomSheetState = playerBottomSheetState,
                                        onShowDetailsDialog = {
                                            mediaMetadata?.id?.let {
                                                bottomSheetPageState.show {
                                                    ShowMediaInfo(it)
                                                }
                                            }
                                        },
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = moreDescription,
                            )
                        }
                    }
                }

                // ── Tab header (YT Music exact): UP NEXT / LYRICS / RELATED. Styled like the app's
                // existing tab idiom (SearchScreen SecondaryTabRow: transparent container + 32dp
                // rounded pill indicator), each Tab TV/D-pad focusable. Static — no polling/animation
                // while idle.
                run {
                    val isTvOrCarTabs = iad1tya.echo.music.ui.utils.rememberIsTvOrCar()
                    SecondaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        indicator = {
                            Box(
                                modifier = Modifier
                                    .tabIndicatorOffset(selectedTab)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        },
                    ) {
                        listOf(
                            R.string.queue_tab_next,
                            R.string.queue_tab_lyrics,
                            R.string.queue_tab_related,
                        ).forEachIndexed { index, titleRes ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = { Text(stringResource(titleRes).uppercase()) },
                                modifier = Modifier.tvFocusable(isTvOrCarTabs),
                            )
                        }
                    }
                }
                // Selection mode only exists on the UP NEXT tab — leave it when switching away.
                LaunchedEffect(selectedTab) {
                    if (selectedTab != QUEUE_TAB_NEXT) onExitSelectionMode()
                }

                if (selectedTab == QUEUE_TAB_NEXT) {
                FlowRow(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    ToggleButton(
                        checked = shuffleModeEnabled,
                        onCheckedChange = {
                            playerConnection.player.shuffleModeEnabled = it
                        },
                        enabled = !isListenTogetherGuest,
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                        Text(
                            text = stringResource(R.string.shuffle),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                            )
                    }

                    ToggleButton(
                        checked = repeatMode != Player.REPEAT_MODE_OFF,
                        onCheckedChange = {
                            playerConnection.player.toggleRepeatMode()
                        },
                        enabled = !isListenTogetherGuest,
                        shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Icon(
                            painter = painterResource(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                    else -> R.drawable.repeat
                                }
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                        Text(
                            text = stringResource(R.string.repeat),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    ToggleButton(
                        checked = false,
                        onCheckedChange = {
                            Toast.makeText(context, context.getString(R.string.starting_radio), Toast.LENGTH_SHORT).show()
                            playerConnection.startRadioSeamlessly()
                        },
                        enabled = !isListenTogetherGuest,
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.radio),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                            Text(
                                text = stringResource(R.string.start_an_radio),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }


                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.continue_playing),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.next_in_queue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.n_song,
                                queueWindows.size,
                                queueWindows.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = makeTimeString(queueLength * 1000L),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(
                    visible = inSelectMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    val selectedSongs = remember(selection.toList(), mutableQueueWindows) {
                        mutableQueueWindows.filter { it.mediaItem.mediaId in selection }
                            .mapNotNull { it.mediaItem.metadata }
                    }
                    val selectedItems = remember(selection.toList(), mutableQueueWindows) {
                        mutableQueueWindows.filter { it.mediaItem.mediaId in selection }
                    }
                    val count = selection.size
                    Row(
                        modifier =
                        Modifier
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onExitSelectionMode,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                            )
                        }
                        Text(
                            text = pluralStringResource(R.plurals.n_selected, count, count),
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = count == mutableQueueWindows.size && count > 0,
                            onCheckedChange = {
                                if (count == mutableQueueWindows.size) {
                                    selection.clear()
                                } else {
                                    selection.clear()
                                    mutableQueueWindows.forEach {
                                        selection.add(it.mediaItem.mediaId)
                                    }
                                }
                            }
                        )
                        IconButton(
                            enabled = count > 0,
                            onClick = {
                                menuState.show {
                                    SelectionMediaMetadataMenu(
                                        songSelection = selectedSongs,
                                        onDismiss = menuState::dismiss,
                                        clearAction = onExitSelectionMode,
                                        currentItems = selectedItems,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                                tint = LocalContentColor.current,
                            )
                        }
                    }
                }
                } // if (selectedTab == QUEUE_TAB_NEXT)
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    // ── LYRICS tab: hosts the SAME InlineLyricsView the player embeds (Player.kt),
                    // honoring its positionProvider contract — null falls through to the component's
                    // LIVE frame-aligned position, NEVER the 500ms-throttled ticker; a value is only
                    // supplied while casting (the active stream), mirroring the player call site.
                    // Only composed while this tab is selected AND the sheet is not collapsed
                    // (BottomSheet skips content when collapsed), so the lyrics ticker never runs
                    // in a hidden sheet (heat/battery).
                    QUEUE_TAB_LYRICS -> {
                        val castPosition by castHandler?.castPosition?.collectAsState()
                            ?: remember { mutableLongStateOf(0L) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(state.preUpPostDownNestedScrollConnection)
                                .windowInsetsPadding(
                                    WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                                ),
                        ) {
                            InlineLyricsView(
                                mediaMetadata = mediaMetadata,
                                showLyrics = true,
                                positionProvider = { if (isCasting) castPosition else null },
                            )
                        }
                    }

                    // ── RELATED tab: renders the related pool the service ALREADY fetched for
                    // autoplay (automixItems — YouTube.next related items, taste-ordered). ZERO new
                    // network: this only mirrors data the autoplay footer shows. Rows reuse the exact
                    // automix row (play next / add to queue / long-press menu).
                    QUEUE_TAB_RELATED -> {
                        if (automix.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.queue_related_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding =
                                    WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                        .add(
                                            WindowInsets(
                                                top = 8.dp,
                                                bottom = ListItemHeight + 8.dp,
                                            ),
                                        ).asPaddingValues(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
                                    // TV/car: restore the D-pad focus ring when scrolling. No-op on phone.
                                    .tvFocusRestorer(),
                            ) {
                                itemsIndexed(
                                    items = automix,
                                    key = { _, it -> it.mediaId },
                                ) { index, item ->
                                    AutomixSongRow(
                                        item = item,
                                        index = index,
                                        total = automix.size,
                                        isListenTogetherGuest = isListenTogetherGuest,
                                        isCasting = isCasting,
                                        navController = navController,
                                        playerBottomSheetState = playerBottomSheetState,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }

                    // ── UP NEXT tab (default): the current queue list + autoplay footer, unchanged.
                    else -> Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    contentPadding =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                            .add(
                                WindowInsets(
                                    top = 8.dp,
                                    bottom = ListItemHeight + 8.dp,
                                ),
                            ).asPaddingValues(),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(state.preUpPostDownNestedScrollConnection)
                        // TV/car: restore the D-pad focus ring to the last row when scrolling the queue. No-op on phone.
                        .tvFocusRestorer()
                ) {
                    item(key = "queue_top_spacer") {
                        Spacer(
                            modifier =
                                Modifier
                                    .animateContentSize()
                                    .height(if (inSelectMode) 48.dp else 0.dp),
                        )
                    }

                    itemsIndexed(
                        items = mutableQueueWindows,
                        key = { _, item -> item.uid.hashCode() },
                    ) { index, window ->
                        ReorderableItem(
                            state = reorderableState,
                            key = window.uid.hashCode(),
                        ) {
                            val currentItem by rememberUpdatedState(window)
                            val isActive = window.uid == currentPlayingUid
                            val dismissBoxState =
                                rememberSwipeToDismissBoxState(
                                    positionalThreshold = { totalDistance -> totalDistance }
                                )

                            var processedDismiss by remember { mutableStateOf(false) }
                            LaunchedEffect(dismissBoxState.currentValue) {
                                val dv = dismissBoxState.currentValue
                                if (!processedDismiss && !isListenTogetherGuest && (
                                            dv == SwipeToDismissBoxValue.StartToEnd ||
                                                    dv == SwipeToDismissBoxValue.EndToStart
                                            )
                                ) {
                                    processedDismiss = true
                                    playerConnection.player.removeMediaItem(currentItem.firstPeriodIndex)
                                    dismissJob?.cancel()
                                    dismissJob = coroutineScope.launch {
                                        val snackbarResult = snackbarHostState.showSnackbar(
                                            message = context.getString(
                                                R.string.removed_song_from_playlist,
                                                currentItem.mediaItem.metadata?.title,
                                            ),
                                            actionLabel = context.getString(R.string.undo),
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (snackbarResult == SnackbarResult.ActionPerformed) {
                                            playerConnection.player.addMediaItem(currentItem.mediaItem)
                                            playerConnection.player.moveMediaItem(
                                                mutableQueueWindows.size,
                                                currentItem.firstPeriodIndex,
                                            )
                                        }
                                    }
                                }
                                if (dv == SwipeToDismissBoxValue.Settled) {
                                    processedDismiss = false
                                }
                            }

                            val onCheckedChange: (Boolean) -> Unit = {
                                if (it) {
                                    selection.add(window.mediaItem.mediaId)
                                } else {
                                    selection.remove(window.mediaItem.mediaId)
                                }
                            }

                            val content: @Composable () -> Unit = {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.animateItem(),
                                ) {
                                    MediaMetadataListItem(
                                        mediaMetadata = window.mediaItem.metadata!!,
                                        isSelected = false,
                                        isActive = isActive,
                                        isPlaying = isPlaying && isActive,
                                        shape = listItemShape(index, mutableQueueWindows.size),
                                        trailingContent = {
                                            if (inSelectMode) {
                                                Checkbox(
                                                    checked = window.mediaItem.mediaId in selection,
                                                    onCheckedChange = onCheckedChange
                                                )
                                            } else {
                                                if (!isListenTogetherGuest) {
                                                    IconButton(
                                                        onClick = {
                                                            menuState.show {
                                                                QueueMenu(
                                                                    mediaMetadata = window.mediaItem.metadata!!,
                                                                    navController = navController,
                                                                    playerBottomSheetState = playerBottomSheetState,
                                                                    onShowDetailsDialog = {
                                                                        window.mediaItem.mediaId.let {
                                                                            bottomSheetPageState.show {
                                                                                ShowMediaInfo(it)
                                                                            }
                                                                        }
                                                                    },
                                                                    onDismiss = menuState::dismiss,
                                                                )
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.more_vert),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                }
                                                if (!locked && !isListenTogetherGuest) {
                                                    IconButton(
                                                        onClick = { },
                                                        modifier = Modifier.draggableHandle()
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.drag_handle),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .background(background)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (inSelectMode) {
                                                            onCheckedChange(window.mediaItem.mediaId !in selection)
                                                        } else if (!isListenTogetherGuest) {
                                                            if (index == currentWindowIndex) {
                                                                if (isCasting) {
                                                                    if (castIsPlaying) {
                                                                        castHandler?.pause()
                                                                    } else {
                                                                        castHandler?.play()
                                                                    }
                                                                } else {
                                                                    playerConnection.togglePlayPause()
                                                                }
                                                            } else {
                                                                if (isCasting) {
                                                                    val mediaId =
                                                                        window.mediaItem.mediaId
                                                                    val navigated =
                                                                        castHandler?.navigateToMediaIfInQueue(
                                                                            mediaId
                                                                        ) ?: false
                                                                    if (!navigated) {
                                                                        playerConnection.player.seekToDefaultPosition(
                                                                            window.firstPeriodIndex
                                                                        )
                                                                    }
                                                                } else {
                                                                    playerConnection.player.seekToDefaultPosition(
                                                                        window.firstPeriodIndex,
                                                                    )
                                                                    playerConnection.player.playWhenReady =
                                                                        true
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (!inSelectMode) {
                                                            haptic.performHapticFeedback(
                                                                HapticFeedbackType.LongPress
                                                            )
                                                            inSelectMode = true
                                                            onCheckedChange(true)
                                                        }
                                                    },
                                                ),
                                    )
                                }
                            }

                            if (locked) {
                                content()
                            } else {
                                SwipeToDismissBox(
                                    state = dismissBoxState,
                                    backgroundContent = {},
                                ) {
                                    content()
                                }
                            }
                        }
                    }

                    // ── Autoplay footer (YT Music parity) — one coherent block pinned at the end of the
                    //    queue: divider + "Autoplay" header with the AutoLoadMore toggle + steering chips +
                    //    the automix preview rows ("what autoplay will play next"). Chips/preview are fed by
                    //    service flows only — no network from the UI layer.
                    if (!isListenTogetherGuest || automix.isNotEmpty()) {
                        item(key = "autoplay_header") {
                            Column(modifier = Modifier.animateItem()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.autoplay_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!isListenTogetherGuest) {
                                        // Same pref as the Settings toggle (AutoLoadMoreKey); Switch is
                                        // natively focusable — tvFocusable adds the D-pad focus ring.
                                        Switch(
                                            checked = autoLoadMore,
                                            onCheckedChange = onAutoLoadMoreChange,
                                            modifier =
                                                Modifier.tvFocusable(
                                                    iad1tya.echo.music.ui.utils.rememberIsTvOrCar(),
                                                    CircleShape,
                                                ),
                                        )
                                    }
                                }
                            }
                        }

                        // Steering chips: only when autoplay is ON and the service published suggestions.
                        // ChipsRow is horizontally scrollable and already TV-focusable per chip.
                        if (!isListenTogetherGuest && autoLoadMore && autoplayChips.isNotEmpty()) {
                            item(key = "autoplay_chips") {
                                ChipsRow(
                                    chips = autoplayChips.map { it to it.label },
                                    currentValue = autoplaySelectedChip,
                                    onValueUpdate = { chip ->
                                        if (chip != null) {
                                            playerConnection.selectAutoplayChip(chip)
                                        }
                                    },
                                )
                            }
                        }

                        // Preview rows: the upcoming auto-tracks (existing automix behavior, unchanged —
                        // row extracted to AutomixSongRow, shared verbatim with the RELATED tab).
                        itemsIndexed(
                            items = automix,
                            key = { _, it -> it.mediaId },
                        ) { index, item ->
                            AutomixSongRow(
                                item = item,
                                index = index,
                                total = automix.size,
                                isListenTogetherGuest = isListenTogetherGuest,
                                isCasting = isCasting,
                                navController = navController,
                                playerBottomSheetState = playerBottomSheetState,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
                    } // else Box (UP NEXT tab)
                } // when (selectedTab)

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                    Modifier
                        .padding(
                            bottom =
                            ListItemHeight +
                                    WindowInsets.systemBars
                                        .asPaddingValues()
                                        .calculateBottomPadding(),
                        )
                        .align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showCommentSheet) {
        CommentSheet(
            videoId = mediaMetadata?.id ?: "",
            onDismiss = { showCommentSheet = false }
        )
    }
}

// Queue sheet tab indices (YT Music parity): UP NEXT is the default.
private const val QUEUE_TAB_NEXT = 0
private const val QUEUE_TAB_LYRICS = 1
private const val QUEUE_TAB_RELATED = 2

/**
 * Index of [mediaId] inside the automix pool AS IT IS RIGHT NOW, or `-1` when the song is no longer
 * there — the only value that may be handed to `MusicService.playNextAutomix` /
 * `addToQueueAutomix`, whose `removeAt(position)` is unchecked.
 *
 * Extracted (and `internal`) so the rule is pinned by a test: the pool is mutated from outside this
 * sheet, so the COMPOSITION index of a row can be stale by the time the row is tapped.
 */
internal fun automixPoolPosition(
    poolMediaIds: List<String>,
    mediaId: String,
): Int = poolMediaIds.indexOfFirst { it == mediaId }

/**
 * One related/automix song row — the EXACT row the autoplay footer used to inline: play-next and
 * add-to-queue trailing actions (service pool operations, ZERO network) + long-press QueueMenu.
 * Shared by the UP NEXT footer and the RELATED tab so the two can never drift apart.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AutomixSongRow(
    item: MediaItem,
    index: Int,
    total: Int,
    isListenTogetherGuest: Boolean,
    isCasting: Boolean,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    // Where this song sits in the SERVICE pool right now, resolved by identity at CLICK time.
    //
    // `index` is the COMPOSITION index and must never reach MusicService.playNextAutomix /
    // addToQueueAutomix: both do an unchecked `removeAt(position)` on `automixItems`, and that list
    // is drained from outside this sheet — Player.kt auto-queues `automix[0]` when the queue runs
    // dry, and the service re-publishes the whole list on every track change. A tap that landed
    // between the pool shrinking and this row recomposing removed the WRONG song (the tapped one
    // survived and could be queued twice), and a tap on the last row after a shrink called
    // `removeAt(size)` → IndexOutOfBoundsException on the main thread, inside a click handler.
    //
    // NOT a composable read: only ever invoked from a click handler, so it never subscribes this
    // row to the pool and the list keeps its current recomposition behaviour.
    val poolPosition = {
        automixPoolPosition(
            playerConnection.service.automixItems.value.map { it.mediaId },
            item.mediaId,
        )
    }

    Row(
        horizontalArrangement = Arrangement.Center,
    ) {
        MediaMetadataListItem(
            mediaMetadata = item.metadata!!,
            shape = listItemShape(index, total),
            trailingContent = {
                if (!isListenTogetherGuest) {
                    IconButton(
                        onClick = {
                            val at = poolPosition()
                            if (at >= 0) {
                                playerConnection.service.playNextAutomix(item, at)
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.playlist_play),
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = {
                            val at = poolPosition()
                            if (at >= 0) {
                                playerConnection.service.addToQueueAutomix(item, at)
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                        )
                    }
                }
            },
            modifier =
                modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        // Was `{}`. `onLongClick` is non-null, so the modifier was attached and the
                        // row rippled like something tappable while doing NOTHING: in the classic
                        // sheet the whole RELATED tab and the UP NEXT autoplay footer were a
                        // placebo, and the only way in was the two trailing icons or a long press.
                        // Plays the tapped song exactly like the new UI does: move it out of the
                        // pool to the front of the queue, then step onto it.
                        onClick = {
                            if (!isListenTogetherGuest) {
                                val at = poolPosition()
                                if (at >= 0) {
                                    playerConnection.service.playNextAutomix(item, at)
                                    playerConnection.player.seekToNext()
                                    if (!isCasting) {
                                        playerConnection.player.playWhenReady = true
                                    }
                                }
                            }
                        },
                        onLongClick = {
                            menuState.show {
                                QueueMenu(
                                    mediaMetadata = item.metadata!!,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
                                    onShowDetailsDialog = {
                                        item.mediaId.let {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(it)
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
        )
    }
}

@Composable
private fun PlayerQueueButton(
    icon: Int,
    onClick: () -> Unit,
    isActive: Boolean,
    enabled: Boolean = true,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    text: String? = null,
    textButtonColor: Color,
    iconButtonColor: Color,
    iconSize: androidx.compose.ui.unit.Dp,
    textBackgroundColor: Color,
    playerBackground: PlayerBackgroundStyle
) {
    // TV/car: visible focus ring observing the .clickable (queue / lyrics / sleep-timer / cast buttons).
    val isTvQueueBtn = iad1tya.echo.music.ui.utils.rememberIsTvOrCar()
    val buttonModifier = Modifier
        .clip(shape)
        .tvFocusable(isTvQueueBtn, shape)
        .clickable(enabled = enabled, onClick = onClick)

    val alphaFactor = if (enabled) 1f else 0.35f

    val appliedModifier = if (isActive) {
        modifier.then(buttonModifier.background(textButtonColor)).alpha(alphaFactor)
    } else {
        modifier.then(
            buttonModifier.border(
                width = 1.dp,
                color = textButtonColor.copy(alpha = 0.3f),
                shape = shape
            )
        ).alpha(alphaFactor)
    }

    Box(
        modifier = appliedModifier,
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            Text(
                text = text,
                color = iconButtonColor.copy(alpha = if (enabled) 1f else 0.6f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
            )
        } else {
            val baseTint = if (isActive) {
                iconButtonColor
            } else {
                when (playerBackground) {
                    PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC, PlayerBackgroundStyle.LIVE_MESH, PlayerBackgroundStyle.LIQUID_GLASS ->
                        Color.White
                    PlayerBackgroundStyle.DEFAULT ->
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
            }
            val finalTint = if (enabled) baseTint else baseTint.copy(alpha = 0.5f)
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = finalTint
            )
        }
    }
}
