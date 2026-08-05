package iad1tya.echo.music.ui.newui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AutoLoadMoreKey
import iad1tya.echo.music.constants.ListItemHeight
import iad1tya.echo.music.constants.PlayerBackgroundStyle
import iad1tya.echo.music.constants.QueueEditLockKey
import iad1tya.echo.music.constants.ShowCommentButtonKey
import iad1tya.echo.music.echomusic.AudioDeviceBottomSheet
import iad1tya.echo.music.echomusic.isBluetoothHeadphoneConnected
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.move
import iad1tya.echo.music.extensions.toggleRepeatMode
import iad1tya.echo.music.listentogether.RoomRole
import iad1tya.echo.music.ui.component.ActionPromptDialog
import iad1tya.echo.music.ui.component.BottomSheet
import iad1tya.echo.music.ui.component.BottomSheetState
import iad1tya.echo.music.ui.component.ChipsRow
import iad1tya.echo.music.ui.component.LocalBottomSheetPageState
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.QueueMenu
import iad1tya.echo.music.ui.menu.SelectionMediaMetadataMenu
import iad1tya.echo.music.ui.player.InlineLyricsView
import iad1tya.echo.music.ui.screens.CommentSheet
import iad1tya.echo.music.ui.utils.ShowMediaInfo
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

/**
 * # Cola — "Interfaz nueva"
 *
 * A drop-in replacement for [iad1tya.echo.music.ui.player.Queue]: identical parameters, identical
 * structural role (a [BottomSheet] whose collapsed content is the bar under the player transport and
 * whose expanded content is the queue sheet), and — critically — identical **actions**. Every control
 * below calls the exact same `playerConnection` / `service` / menu function the classic queue calls.
 * There is no second copy of any behaviour here; this file is presentation only.
 *
 * ## What this screen shows that the app never has
 * The engine has always known which queue items are the user's own list and which the infinite radio
 * appended — `MusicService.applyShuffleOrder`'s "playlist first" branch partitions on exactly that
 * boundary. It was never surfaced. `PlayerConnection.listQueueSize` now publishes it (read-only, no
 * behaviour attached), and this screen draws it: **A CONTINUACIÓN · DE TU LISTA** in white, then
 * **DESPUÉS · RADIO INFINITA** in violet with the rows dimmed, exactly as the reference render does.
 *
 * ## Two things deliberately carried, not redesigned
 *  1. **The audio-output picker.** In the classic player it exists only in the OLD collapsed bar
 *     (`useNewPlayerDesign == false`), so most users can never reach it. The Aura bar below carries the
 *     union of BOTH classic bars, so the picker is always one tap away regardless of that preference.
 *  2. **The queue's ⋮ goes through [PlayerMenuHost].** The classic queue opens `PlayerMenu` — the only
 *     menu in the app with *Modo ambiente*, *Ecualizador* and *Velocidad y tono*. The host resolves to
 *     the merged `AuraPlayerMenu`, which carries all three (plus everything `OldPlayerMenu` had), so
 *     the render's single-menu intent holds without deleting anything. This screen only ever exists
 *     with the flag ON, so the host never falls back here.
 *
 * ## Thermal / battery
 * The bloom is resolved once per track ([rememberAuraBloom]) and drawn without `Modifier.blur`. The
 * "sonando" bars are static, not an animation. The lyrics tab is only composed while its tab is
 * selected AND the sheet is open (the sheet skips content when collapsed) — same guarantee as classic.
 */
@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AuraQueue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    // The player's own sheet colours. The expanded queue is drawn on the Aura ground instead (the
    // render gives it its own surface), so only the collapsed bar — which sits ON the player — uses
    // them; they stay in the signature so this is a drop-in for the classic Queue.
    @Suppress("UNUSED_PARAMETER") background: Color,
    @Suppress("UNUSED_PARAMETER") onBackgroundColor: Color,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    pureBlack: Boolean,
    showInlineLyrics: Boolean,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") playerBackground: PlayerBackgroundStyle = PlayerBackgroundStyle.DEFAULT,
    onToggleLyrics: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    var showAudioDeviceBottomSheet by remember { mutableStateOf(false) }

    // Verbatim from the classic queue: the headset/speaker glyph of the audio-output button must follow
    // the real device state, so the same receiver + AudioDeviceCallback pair feeds it.
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
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
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
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()

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
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    var locked by rememberPreference(QueueEditLockKey, defaultValue = false)
    val (showCommentButton) = rememberPreference(ShowCommentButtonKey, defaultValue = false)

    // Tabs hoisted outside the sheet content (which is not composed while collapsed) so the
    // collapse-reset always runs — reopening lands on SIGUIENTE and no hidden ticker survives.
    var selectedTab by rememberSaveable { mutableIntStateOf(AURA_QUEUE_TAB_NEXT) }
    LaunchedEffect(state.isCollapsed) {
        if (state.isCollapsed) selectedTab = AURA_QUEUE_TAB_NEXT
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerValue by remember { mutableFloatStateOf(30f) }
    val sleepTimerEnabled = remember(
        playerConnection.service.sleepTimer.triggerTime,
        playerConnection.service.sleepTimer.pauseWhenSongEnd,
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

    val openPlayerMenu: () -> Unit = {
        menuState.show {
            PlayerMenuHost(
                mediaMetadata = mediaMetadata,
                navController = navController,
                playerBottomSheetState = playerBottomSheetState,
                onShowDetailsDialog = {
                    mediaMetadata?.id?.let {
                        bottomSheetPageState.show { ShowMediaInfo(it) }
                    }
                },
                onDismiss = menuState::dismiss,
                onSleepTimer = { showSleepTimerDialog = true },
            )
        }
    }

    BottomSheet(
        state = state,
        modifier = modifier,
        background = { Box(Modifier.fillMaxSize().background(Color.Unspecified)) },
        collapsedContent = {
            AuraQueueBar(
                onOpenQueue = { state.expandSoft() },
                isBluetoothConnected = isBluetoothConnected,
                onAudioOutput = { showAudioDeviceBottomSheet = true },
                sleepTimerEnabled = sleepTimerEnabled,
                sleepTimerTimeLeft = sleepTimerTimeLeft,
                onSleepTimer = {
                    if (sleepTimerEnabled) {
                        playerConnection.service.sleepTimer.clear()
                    } else {
                        showSleepTimerDialog = true
                    }
                },
                showInlineLyrics = showInlineLyrics,
                onToggleLyrics = onToggleLyrics,
                showCommentButton = showCommentButton,
                onComments = { showCommentSheet = true },
                shuffleModeEnabled = shuffleModeEnabled,
                onToggleShuffle = {
                    playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                },
                repeatMode = repeatMode,
                onToggleRepeat = { playerConnection.player.toggleRepeatMode() },
                onMore = openPlayerMenu,
                isListenTogetherGuest = isListenTogetherGuest,
                contentColor = textBackgroundColor,
                activeContainerColor = textButtonColor,
                activeContentColor = iconButtonColor,
            )

            if (showAudioDeviceBottomSheet) {
                AudioDeviceBottomSheet(onDismiss = { showAudioDeviceBottomSheet = false })
            }

            if (showSleepTimerDialog) {
                // §4.3 verbatim: the same ActionPromptDialog, the same 5..120 slider in steps of 5, the
                // same "Al finalizar la canción" escape hatch and the same reset-to-30.
                ActionPromptDialog(
                    titleBar = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.sleep_timer),
                                overflow = AuraDefaultOverflow,
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
                    onCancel = { showSleepTimerDialog = false },
                    onReset = { sleepTimerValue = 30f },
                    content = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.minute,
                                    sleepTimerValue.roundToInt(),
                                    sleepTimerValue.roundToInt(),
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(16.dp))
                            Slider(
                                value = sleepTimerValue,
                                onValueChange = { sleepTimerValue = it },
                                valueRange = 5f..120f,
                                steps = (120 - 5) / 5 - 1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    showSleepTimerDialog = false
                                    playerConnection.service.sleepTimer.start(-1)
                                },
                            ) {
                                Text(stringResource(R.string.end_of_song))
                            }
                        }
                    },
                )
            }
        },
    ) {
        val queueWindows by playerConnection.queueWindows.collectAsState()
        val automix by playerConnection.service.automixItems.collectAsState()
        val autoplayChips by playerConnection.autoplayChips.collectAsState()
        val autoplaySelectedChip by playerConnection.autoplaySelectedChip.collectAsState()
        val listQueueSize by playerConnection.listQueueSize.collectAsState()
        val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(AutoLoadMoreKey, defaultValue = true)

        val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
        val queueLength = remember(queueWindows) {
            queueWindows.sumOf { it.mediaItem.metadata!!.duration }
        }

        val coroutineScope = rememberCoroutineScope()
        val lazyListState = rememberLazyListState()
        var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        val currentPlayingUid = remember(currentWindowIndex, queueWindows) {
            if (currentWindowIndex in queueWindows.indices) queueWindows[currentWindowIndex].uid else null
        }

        // Key-based index mapping instead of the classic "lazy index − headerItems" arithmetic: this list
        // interleaves section labels, so a fixed header count would be wrong. Keys are stable strings, so
        // a drop over a non-song item resolves to -1 and is ignored rather than moving the wrong track.
        fun queueIndexOfKey(key: Any?): Int =
            mutableQueueWindows.indexOfFirst { auraSongKey(it) == key }

        val reorderableState = rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = WindowInsets.systemBars.add(
                WindowInsets(top = ListItemHeight, bottom = ListItemHeight),
            ).asPaddingValues(),
        ) { from, to ->
            val safeFrom = queueIndexOfKey(from.key)
            val safeTo = queueIndexOfKey(to.key)
            if (safeFrom >= 0 && safeTo >= 0) {
                val currentDragInfo = dragInfo
                dragInfo = if (currentDragInfo == null) safeFrom to safeTo else currentDragInfo.first to safeTo
                mutableQueueWindows.move(safeFrom, safeTo)
            }
        }

        // Commit the reorder to the player exactly the way the classic queue does: a plain move when
        // playing in order, a rebuilt DefaultShuffleOrder while shuffling.
        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging) {
                dragInfo?.let { (from, to) ->
                    val safeFrom = from.coerceIn(0, queueWindows.lastIndex.coerceAtLeast(0))
                    val safeTo = to.coerceIn(0, queueWindows.lastIndex.coerceAtLeast(0))
                    if (!playerConnection.player.shuffleModeEnabled) {
                        playerConnection.player.moveMediaItem(safeFrom, safeTo)
                    } else {
                        playerConnection.player.setShuffleOrder(
                            DefaultShuffleOrder(
                                queueWindows.map { it.firstPeriodIndex }
                                    .toMutableList()
                                    .move(safeFrom, safeTo)
                                    .toIntArray(),
                                System.currentTimeMillis(),
                            ),
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

        // The grouped row plan: computed once per (queue, boundary, current song), never per frame.
        val entries = remember(mutableQueueWindows.toList(), listQueueSize, currentWindowIndex) {
            buildAuraQueueEntries(
                windows = mutableQueueWindows.toList(),
                listQueueSize = listQueueSize,
                currentIndex = currentWindowIndex,
            )
        }

        // Land on the current song when the sheet opens — and ONLY then. The classic effect keys on the
        // (stable) list instance so it fires once per open; ours has to key on `entries`, which changes
        // on every track advance and every reorder, so the one-shot is explicit. Without it the list
        // would yank itself back to the current song while the user was scrolling or dragging.
        var didInitialScroll by remember { mutableStateOf(false) }
        LaunchedEffect(entries) {
            if (didInitialScroll) return@LaunchedEffect
            val target = entries.indexOfFirst { it is AuraQueueEntry.Song && it.isCurrent }
            if (target >= 0) {
                lazyListState.scrollToItem(target + AURA_QUEUE_LEADING_ITEMS)
                didInitialScroll = true
            }
        }

        // Pure black is a real user setting: honour it by dropping the ground AND the bloom, instead of
        // painting black over a bloom that was computed for nothing.
        val bloom = rememberAuraBloom(mediaMetadata?.id)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (pureBlack) Modifier.background(Color.Black)
                    // Render: the Cola screen dims its bloom to .45.
                    else Modifier.auraScreenBackground(colors = bloom, intensity = 0.45f)
                ),
        ) {
            Column(
                modifier = Modifier
                    // Swallow taps on the header so they never reach the sheet drag/collapse handler.
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
            ) {
                // ── Sheet header: "En cola" + aleatorio / repetir / bloquear / más ────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AuraSpacing.Gutter, end = 6.dp, top = 6.dp),
                ) {
                    Text(
                        text = "En cola",
                        style = AuraType.SheetTitle,
                        color = AuraPalette.OnGround,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                        modifier = Modifier.weight(1f),
                    )
                    AuraIconButton(
                        icon = AuraIcons.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                        enabled = !isListenTogetherGuest,
                        size = 22.dp,
                        tint = if (shuffleModeEnabled) AuraPalette.Teal else AuraPalette.OnGroundDisabled,
                    )
                    // 3-state, like the classic control. The render has no "repeat one" glyph, so that
                    // third state borrows the app's own `repeat_one` drawable rather than being invisible.
                    if (repeatMode == Player.REPEAT_MODE_ONE) {
                        AuraPainterIconButton(
                            painterId = R.drawable.repeat_one,
                            contentDescription = stringResource(R.string.repeat),
                            onClick = { playerConnection.player.toggleRepeatMode() },
                            enabled = !isListenTogetherGuest,
                            size = 22.dp,
                            tint = AuraPalette.Teal,
                        )
                    } else {
                        AuraIconButton(
                            icon = AuraIcons.Repeat,
                            contentDescription = stringResource(R.string.repeat),
                            onClick = { playerConnection.player.toggleRepeatMode() },
                            enabled = !isListenTogetherGuest,
                            size = 22.dp,
                            tint = if (repeatMode == Player.REPEAT_MODE_ALL) AuraPalette.Teal
                            else AuraPalette.OnGroundDisabled,
                        )
                    }
                    val lockDescription = if (locked) stringResource(R.string.unlock_queue)
                    else stringResource(R.string.lock_queue)
                    AuraPainterIconButton(
                        painterId = if (locked) R.drawable.lock else R.drawable.lock_open,
                        contentDescription = lockDescription,
                        onClick = { locked = !locked },
                        size = 20.dp,
                        tint = if (locked) AuraPalette.Teal else AuraPalette.OnGroundMuted,
                    )
                    AuraIconButton(
                        icon = AuraIcons.More,
                        contentDescription = stringResource(R.string.more_options),
                        onClick = openPlayerMenu,
                        size = 22.dp,
                        tint = AuraPalette.OnGround,
                    )
                }

                // ── SONANDO ───────────────────────────────────────────────────────────────────────
                Column(modifier = Modifier.padding(horizontal = AuraSpacing.Gutter, vertical = 6.dp)) {
                    AuraSectionLabel("SONANDO")
                    Spacer(Modifier.height(AuraSpacing.SectionGap))
                    AuraRow(
                        title = mediaMetadata?.title.orEmpty(),
                        subtitle = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                        highlighted = true,
                        artwork = { AuraCover(url = mediaMetadata?.thumbnailUrl, seed = mediaMetadata?.id, size = 44.dp) },
                        trailing = {
                            val liked = currentSong?.song?.liked == true
                            AuraPlayingBars(visible = isPlaying)
                            AuraIconButton(
                                icon = if (liked) AuraIcons.HeartFilled else AuraIcons.Heart,
                                contentDescription = if (liked) stringResource(R.string.action_remove_like)
                                else stringResource(R.string.action_like),
                                onClick = playerConnection::toggleLike,
                                size = 20.dp,
                                tint = if (liked) AuraPalette.Teal else AuraPalette.OnGroundMuted,
                            )
                        },
                    )
                }

                // ── Tabs: SIGUIENTE / LETRA / RELACIONADOS ────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = AuraSpacing.Gutter),
                ) {
                    val isTvOrCarTabs = iad1tya.echo.music.ui.utils.rememberIsTvOrCar()
                    listOf(
                        R.string.queue_tab_next,
                        R.string.queue_tab_lyrics,
                        R.string.queue_tab_related,
                    ).forEachIndexed { index, titleRes ->
                        AuraChip(
                            text = stringResource(titleRes),
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            modifier = Modifier.tvFocusable(isTvOrCarTabs, AuraShapes.Pill),
                        )
                    }
                }

                LaunchedEffect(selectedTab) {
                    if (selectedTab != AURA_QUEUE_TAB_NEXT) onExitSelectionMode()
                }

                // ── Selection bar (SIGUIENTE only) ────────────────────────────────────────────────
                if (selectedTab == AURA_QUEUE_TAB_NEXT) {
                    AnimatedVisibility(
                        visible = inSelectMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        val selectedSongs = remember(selection.toList(), mutableQueueWindows.toList()) {
                            mutableQueueWindows.filter { it.mediaItem.mediaId in selection }
                                .mapNotNull { it.mediaItem.metadata }
                        }
                        val selectedItems = remember(selection.toList(), mutableQueueWindows.toList()) {
                            mutableQueueWindows.filter { it.mediaItem.mediaId in selection }
                        }
                        val count = selection.size
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 6.dp, top = 4.dp),
                        ) {
                            AuraPainterIconButton(
                                painterId = R.drawable.close,
                                contentDescription = "Salir de la selección",
                                onClick = onExitSelectionMode,
                                size = 20.dp,
                            )
                            Text(
                                text = pluralStringResource(R.plurals.n_selected, count, count),
                                style = AuraType.RowTitle,
                                color = AuraPalette.OnGround,
                                maxLines = 1,
                                overflow = AuraDefaultOverflow,
                                modifier = Modifier.weight(1f),
                            )
                            Checkbox(
                                checked = count == mutableQueueWindows.size && count > 0,
                                onCheckedChange = {
                                    if (count == mutableQueueWindows.size) {
                                        selection.clear()
                                    } else {
                                        selection.clear()
                                        mutableQueueWindows.forEach { selection.add(it.mediaItem.mediaId) }
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AuraPalette.Teal,
                                    checkmarkColor = AuraPalette.OnAccent,
                                    uncheckedColor = AuraPalette.OnGroundDisabled,
                                ),
                            )
                            AuraIconButton(
                                icon = AuraIcons.More,
                                contentDescription = stringResource(R.string.more_options),
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
                                size = 22.dp,
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    // LETRA — the same InlineLyricsView the player embeds, with the same
                    // positionProvider contract: null falls through to the component's LIVE position,
                    // never the 500 ms ticker; a value is only supplied while casting.
                    AURA_QUEUE_TAB_LYRICS -> {
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

                    // RELACIONADOS — the pool autoplay already fetched (service flow, zero new network).
                    AURA_QUEUE_TAB_RELATED -> {
                        if (automix.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.queue_related_empty),
                                    style = AuraType.RowSubtitle,
                                    color = AuraPalette.OnGroundMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = WindowInsets.systemBars
                                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                    .add(WindowInsets(top = 8.dp, bottom = ListItemHeight + 8.dp))
                                    .asPaddingValues(),
                                verticalArrangement = Arrangement.spacedBy(AuraSpacing.RowGap),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = AuraSpacing.Gutter)
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
                                    .tvFocusRestorer(),
                            ) {
                                itemsIndexed(items = automix, key = { _, it -> it.mediaId }) { index, item ->
                                    AuraAutomixRow(
                                        item = item,
                                        index = index,
                                        isListenTogetherGuest = isListenTogetherGuest,
                                        navController = navController,
                                        playerBottomSheetState = playerBottomSheetState,
                                    )
                                }
                            }
                        }
                    }

                    // SIGUIENTE — the queue itself, grouped into "de tu lista" and "radio infinita".
                    else -> Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                .add(WindowInsets(top = 4.dp, bottom = ListItemHeight + 8.dp))
                                .asPaddingValues(),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = AuraSpacing.Gutter)
                                .nestedScroll(state.preUpPostDownNestedScrollConnection)
                                .tvFocusRestorer(),
                        ) {
                            // Leading items — keep AURA_QUEUE_LEADING_ITEMS in sync with this count.
                            item(key = "aura_queue_select_spacer") {
                                Spacer(
                                    Modifier
                                        .animateContentSize()
                                        .height(if (inSelectMode) 8.dp else 0.dp),
                                )
                            }
                            item(key = "aura_queue_list_header") {
                                AuraQueueListHeader(
                                    songCount = queueWindows.size,
                                    totalDurationMs = queueLength * 1000L,
                                    radioEnabled = !isListenTogetherGuest,
                                    onStartRadio = {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.starting_radio),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        playerConnection.startRadioSeamlessly()
                                    },
                                )
                            }

                            itemsIndexed(
                                items = entries,
                                key = { _, entry ->
                                    when (entry) {
                                        is AuraQueueEntry.Label -> "aura_queue_label_${entry.id}"
                                        is AuraQueueEntry.Song -> auraSongKey(entry.window)
                                    }
                                },
                            ) { _, entry ->
                                when (entry) {
                                    is AuraQueueEntry.Label -> Column {
                                        Spacer(Modifier.height(AuraSpacing.SectionTop))
                                        AuraSectionLabel(
                                            text = entry.text,
                                            color = if (entry.radio) AuraPalette.Violet.copy(alpha = 0.85f)
                                            else AuraPalette.OnGroundFaint,
                                        )
                                        Spacer(Modifier.height(AuraSpacing.SectionGap))
                                    }

                                    is AuraQueueEntry.Song -> {
                                        val window = entry.window
                                        val index = entry.queueIndex
                                        ReorderableItem(state = reorderableState, key = auraSongKey(window)) {
                                            val currentItem by rememberUpdatedState(window)
                                            val isActive = window.uid == currentPlayingUid
                                            val dismissBoxState = rememberSwipeToDismissBoxState(
                                                positionalThreshold = { totalDistance -> totalDistance },
                                            )

                                            // Swipe-to-remove + Undo — the exact classic effect, so the
                                            // restore lands the song back at its original position.
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
                                                if (dv == SwipeToDismissBoxValue.Settled) processedDismiss = false
                                            }

                                            val onCheckedChange: (Boolean) -> Unit = {
                                                if (it) selection.add(window.mediaItem.mediaId)
                                                else selection.remove(window.mediaItem.mediaId)
                                            }

                                            val dragHandle: (@Composable () -> Unit)? =
                                                if (!locked && !isListenTogetherGuest && !inSelectMode) {
                                                    {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier
                                                                .sizeIn(
                                                                    minWidth = AuraSpacing.MinTouchTarget,
                                                                    minHeight = AuraSpacing.MinTouchTarget,
                                                                )
                                                                .clip(CircleShape)
                                                                .draggableHandle(),
                                                        ) {
                                                            AuraIconGlyph(
                                                                icon = AuraIcons.DragHandle,
                                                                contentDescription = "Reordenar",
                                                                size = 21.dp,
                                                                tint = AuraPalette.OnGroundDisabled,
                                                            )
                                                        }
                                                    }
                                                } else null

                                            val row: @Composable () -> Unit = {
                                                AuraRow(
                                                    title = window.mediaItem.metadata!!.title,
                                                    subtitle = window.mediaItem.metadata!!.artists
                                                        .joinToString { it.name },
                                                    highlighted = isActive,
                                                    dimmed = entry.radio && !isActive,
                                                    leading = dragHandle,
                                                    artwork = {
                                                        AuraCover(
                                                            url = window.mediaItem.metadata!!.thumbnailUrl,
                                                            seed = window.mediaItem.mediaId,
                                                            size = 40.dp,
                                                        )
                                                    },
                                                    trailing = {
                                                        if (inSelectMode) {
                                                            Checkbox(
                                                                checked = window.mediaItem.mediaId in selection,
                                                                onCheckedChange = onCheckedChange,
                                                                colors = CheckboxDefaults.colors(
                                                                    checkedColor = AuraPalette.Teal,
                                                                    checkmarkColor = AuraPalette.OnAccent,
                                                                    uncheckedColor = AuraPalette.OnGroundDisabled,
                                                                ),
                                                            )
                                                        } else {
                                                            if (isActive) AuraPlayingBars(visible = isPlaying)
                                                            if (!isListenTogetherGuest) {
                                                                AuraIconButton(
                                                                    icon = AuraIcons.More,
                                                                    contentDescription = stringResource(R.string.more_options),
                                                                    size = 20.dp,
                                                                    tint = AuraPalette.OnGroundMuted,
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
                                                                    },
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onClick = {
                                                        if (inSelectMode) {
                                                            onCheckedChange(window.mediaItem.mediaId !in selection)
                                                        } else if (!isListenTogetherGuest) {
                                                            if (index == currentWindowIndex) {
                                                                if (isCasting) {
                                                                    if (castIsPlaying) castHandler?.pause()
                                                                    else castHandler?.play()
                                                                } else {
                                                                    playerConnection.togglePlayPause()
                                                                }
                                                            } else {
                                                                if (isCasting) {
                                                                    val navigated = castHandler
                                                                        ?.navigateToMediaIfInQueue(window.mediaItem.mediaId)
                                                                        ?: false
                                                                    if (!navigated) {
                                                                        playerConnection.player
                                                                            .seekToDefaultPosition(window.firstPeriodIndex)
                                                                    }
                                                                } else {
                                                                    playerConnection.player
                                                                        .seekToDefaultPosition(window.firstPeriodIndex)
                                                                    playerConnection.player.playWhenReady = true
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (!inSelectMode) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            inSelectMode = true
                                                            onCheckedChange(true)
                                                        }
                                                    },
                                                )
                                            }

                                            if (locked) {
                                                row()
                                            } else {
                                                SwipeToDismissBox(
                                                    state = dismissBoxState,
                                                    backgroundContent = {},
                                                ) { row() }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Autoplay footer: header + toggle + steering chips + preview rows ──
                            if (!isListenTogetherGuest || automix.isNotEmpty()) {
                                item(key = "aura_autoplay_header") {
                                    Column {
                                        Spacer(Modifier.height(AuraSpacing.SectionTop))
                                        AuraDivider()
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.autoplay_title),
                                                style = AuraType.RowTitle,
                                                color = AuraPalette.OnGround,
                                                maxLines = 1,
                                                overflow = AuraDefaultOverflow,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (!isListenTogetherGuest) {
                                                // The SAME preference as Ajustes → "Cargar más canciones
                                                // automáticamente" (AutoLoadMoreKey); both stay in sync.
                                                AuraSwitch(
                                                    checked = autoLoadMore,
                                                    onCheckedChange = onAutoLoadMoreChange,
                                                    contentDescription = stringResource(R.string.autoplay_title),
                                                    modifier = Modifier.tvFocusable(
                                                        iad1tya.echo.music.ui.utils.rememberIsTvOrCar(),
                                                        CircleShape,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }

                                if (!isListenTogetherGuest && autoLoadMore && autoplayChips.isNotEmpty()) {
                                    item(key = "aura_autoplay_chips") {
                                        ChipsRow(
                                            chips = autoplayChips.map { it to it.label },
                                            currentValue = autoplaySelectedChip,
                                            onValueUpdate = { chip ->
                                                if (chip != null) playerConnection.selectAutoplayChip(chip)
                                            },
                                        )
                                    }
                                }

                                itemsIndexed(items = automix, key = { _, it -> "aura_automix_${it.mediaId}" }) { index, item ->
                                    Column {
                                        Spacer(Modifier.height(AuraSpacing.RowGap))
                                        AuraAutomixRow(
                                            item = item,
                                            index = index,
                                            isListenTogetherGuest = isListenTogetherGuest,
                                            navController = navController,
                                            playerBottomSheetState = playerBottomSheetState,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .padding(
                            bottom = ListItemHeight +
                                WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
                        )
                        .align(Alignment.BottomCenter),
                )
            }

            // ── Footer: "DESLIZA PARA QUITAR" + the lyrics toggle, exactly as the render ──────────
            Column {
                AuraDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.systemBars
                                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        )
                        .padding(start = AuraSpacing.Gutter, end = 6.dp),
                ) {
                    AuraTechnicalText(
                        text = if (locked) "COLA BLOQUEADA" else "DESLIZA PARA QUITAR",
                        color = AuraPalette.OnGroundGhost,
                        modifier = Modifier.weight(1f),
                    )
                    AuraIconButton(
                        icon = AuraIcons.Lyrics,
                        contentDescription = stringResource(R.string.queue_tab_lyrics),
                        onClick = onToggleLyrics,
                        size = 20.dp,
                        tint = if (showInlineLyrics) AuraPalette.Teal else AuraPalette.OnGroundMuted,
                    )
                }
            }
        }
    }

    if (showCommentSheet) {
        CommentSheet(
            videoId = mediaMetadata?.id ?: "",
            onDismiss = { showCommentSheet = false },
        )
    }
}

// ── Collapsed bar ─────────────────────────────────────────────────────────────────────────────────

/**
 * The bar under the player transport. It carries the **union** of the two classic bars (§4.1 the new
 * design, §4.2 the old one) so nothing depends on `UseNewPlayerDesignKey` any more — most importantly
 * the audio-output picker, which today only exists in the old design and is the app's only way to
 * choose an output device.
 *
 * Colours come from the player (not from [AuraPalette]): this bar is drawn ON TOP of whatever
 * background the player renders — a bright cover, a blur, a mesh — so it must use the same
 * contrast-correct content colours the classic bar uses.
 */
@Composable
private fun AuraQueueBar(
    onOpenQueue: () -> Unit,
    isBluetoothConnected: Boolean,
    onAudioOutput: () -> Unit,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onSleepTimer: () -> Unit,
    showInlineLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    showCommentButton: Boolean,
    onComments: () -> Unit,
    shuffleModeEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    repeatMode: Int,
    onToggleRepeat: () -> Unit,
    onMore: () -> Unit,
    isListenTogetherGuest: Boolean,
    contentColor: Color,
    activeContainerColor: Color,
    activeContentColor: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
    ) {
        AuraBarButton(
            contentDescription = "Abrir la cola",
            active = false,
            onClick = onOpenQueue,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint -> AuraIconGlyph(AuraIcons.Queue, null, size = 21.dp, tint = tint) }

        // ⚠️ The app's ONLY audio-output picker. It used to be reachable only with the old player
        // design; here it is unconditional.
        AuraBarButton(
            contentDescription = "Salida de audio",
            active = false,
            onClick = onAudioOutput,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint ->
            Icon(
                painter = painterResource(
                    if (isBluetoothConnected) R.drawable.headset_applemusic else R.drawable.speaker_apple,
                ),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(21.dp),
            )
        }

        AuraBarButton(
            contentDescription = stringResource(R.string.sleep_timer),
            active = sleepTimerEnabled,
            enabled = !isListenTogetherGuest,
            onClick = onSleepTimer,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint ->
            if (sleepTimerEnabled) {
                AuraTechnicalText(text = makeTimeString(sleepTimerTimeLeft), color = tint)
            } else {
                AuraIconGlyph(AuraIcons.Timer, null, size = 21.dp, tint = tint)
            }
        }

        AuraBarButton(
            contentDescription = stringResource(R.string.queue_tab_lyrics),
            active = showInlineLyrics,
            onClick = onToggleLyrics,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint -> AuraIconGlyph(AuraIcons.Lyrics, null, size = 21.dp, tint = tint) }

        if (showCommentButton) {
            AuraBarButton(
                contentDescription = "Comentarios",
                active = false,
                onClick = onComments,
                contentColor = contentColor,
                activeContainerColor = activeContainerColor,
                activeContentColor = activeContentColor,
            ) { tint ->
                Icon(
                    painter = painterResource(R.drawable.chat_msg),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        AuraBarButton(
            contentDescription = stringResource(R.string.shuffle),
            active = shuffleModeEnabled,
            enabled = !isListenTogetherGuest,
            onClick = onToggleShuffle,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint -> AuraIconGlyph(AuraIcons.Shuffle, null, size = 21.dp, tint = tint) }

        AuraBarButton(
            contentDescription = stringResource(R.string.repeat),
            active = repeatMode != Player.REPEAT_MODE_OFF,
            enabled = !isListenTogetherGuest,
            onClick = onToggleRepeat,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint ->
            if (repeatMode == Player.REPEAT_MODE_ONE) {
                Icon(
                    painter = painterResource(R.drawable.repeat_one),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(21.dp),
                )
            } else {
                AuraIconGlyph(AuraIcons.Repeat, null, size = 21.dp, tint = tint)
            }
        }

        Spacer(Modifier.weight(1f))

        AuraBarButton(
            contentDescription = stringResource(R.string.more_options),
            active = true,
            onClick = onMore,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint -> AuraIconGlyph(AuraIcons.More, null, size = 21.dp, tint = tint) }
    }
}

/** One 48 dp button of [AuraQueueBar]: pill fill when active, hairline outline otherwise. */
@Composable
private fun AuraBarButton(
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    contentColor: Color,
    activeContainerColor: Color,
    activeContentColor: Color,
    enabled: Boolean = true,
    content: @Composable (tint: Color) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val tint = if (active) activeContentColor else contentColor
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .sizeIn(minWidth = AuraSpacing.MinTouchTarget, minHeight = AuraSpacing.MinTouchTarget)
            .padding(4.dp)
            .clip(shape)
            .then(
                if (active) Modifier.background(activeContainerColor, shape)
                else Modifier.border(1.dp, contentColor.copy(alpha = 0.28f), shape)
            )
            .tvFocusable(iad1tya.echo.music.ui.utils.rememberIsTvOrCar(), shape)
            .clickable(enabled = enabled, onClickLabel = contentDescription, role = Role.Button, onClick = onClick),
    ) {
        content(if (enabled) tint else tint.copy(alpha = tint.alpha * 0.35f))
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────────────────────────

/**
 * "Continuar reproduciendo / Siguiente en la cola", the song count, the total duration and the Radio
 * action — §4.5's list header and its Radio button in one line.
 */
@Composable
private fun AuraQueueListHeader(
    songCount: Int,
    totalDurationMs: Long,
    radioEnabled: Boolean,
    onStartRadio: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.continue_playing),
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            Text(
                text = stringResource(R.string.next_in_queue),
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            AuraTechnicalText(text = pluralStringResource(R.plurals.n_song, songCount, songCount))
            AuraTechnicalText(text = makeTimeString(totalDurationMs))
        }
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
                .clip(AuraShapes.Pill)
                .background(AuraPalette.SurfaceFill)
                .clickable(
                    enabled = radioEnabled,
                    onClickLabel = stringResource(R.string.start_an_radio),
                    role = Role.Button,
                    onClick = onStartRadio,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            AuraIconGlyph(
                icon = AuraIcons.Radio,
                contentDescription = null,
                size = 17.dp,
                tint = if (radioEnabled) AuraPalette.Violet else AuraPalette.OnGroundDisabled,
            )
            Text(
                text = stringResource(R.string.start_an_radio),
                style = AuraType.Chip,
                color = if (radioEnabled) AuraPalette.OnGround else AuraPalette.OnGroundDisabled,
                maxLines = 1,
            )
        }
    }
}

/** A related/automix row: play-next and add-to-queue (service pool ops, zero network) + long-press menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuraAutomixRow(
    item: MediaItem,
    index: Int,
    isListenTogetherGuest: Boolean,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val metadata = item.metadata ?: return

    AuraRow(
        title = metadata.title,
        subtitle = metadata.artists.joinToString { it.name },
        dimmed = true,
        modifier = modifier,
        artwork = { AuraCover(url = metadata.thumbnailUrl, seed = item.mediaId, size = 40.dp) },
        trailing = {
            if (!isListenTogetherGuest) {
                AuraPainterIconButton(
                    painterId = R.drawable.playlist_play,
                    contentDescription = "Reproducir a continuación",
                    onClick = { playerConnection.service.playNextAutomix(item, index) },
                    size = 20.dp,
                    tint = AuraPalette.OnGroundMuted,
                )
                AuraIconButton(
                    icon = AuraIcons.Queue,
                    contentDescription = "Añadir a la cola",
                    onClick = { playerConnection.service.addToQueueAutomix(item, index) },
                    size = 20.dp,
                    tint = AuraPalette.OnGroundMuted,
                )
            }
        },
        onClick = {},
        onLongClick = {
            menuState.show {
                QueueMenu(
                    mediaMetadata = metadata,
                    navController = navController,
                    playerBottomSheetState = playerBottomSheetState,
                    onShowDetailsDialog = {
                        item.mediaId.let { bottomSheetPageState.show { ShowMediaInfo(it) } }
                    },
                    onDismiss = menuState::dismiss,
                )
            }
        },
    )
}

/** Cover art with the palette's deterministic gradient placeholder underneath while it loads. */
@Composable
private fun AuraCover(url: String?, seed: String?, size: Dp) {
    AuraArtwork(size = size, placeholderSeed = seed) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The three teal bars of the render's "SONANDO" row. **Static on purpose** — an animated equaliser
 * would repaint every frame for the whole time the sheet is open, which is exactly the kind of
 * per-frame cost the thermal gate forbids. Height ratios are the render's: 60 % / 100 % / 45 %.
 */
@Composable
private fun AuraPlayingBars(visible: Boolean) {
    if (!visible) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(17.dp),
    ) {
        listOf(0.60f, 1f, 0.45f).forEach { fraction ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .background(AuraPalette.Teal),
            )
        }
    }
}

/** [AuraIconButton] for glyphs the render's icon set does not define (lock, close, playlist_play…). */
@Composable
private fun AuraPainterIconButton(
    painterId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = AuraPalette.OnGround,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = AuraSpacing.MinTouchTarget, minHeight = AuraSpacing.MinTouchTarget)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(painterId),
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = tint.alpha * 0.35f),
            modifier = Modifier.size(size),
        )
    }
}

// ── The grouped row plan ──────────────────────────────────────────────────────────────────────────

private const val AURA_QUEUE_TAB_NEXT = 0
private const val AURA_QUEUE_TAB_LYRICS = 1
private const val AURA_QUEUE_TAB_RELATED = 2

/** Number of lazy items emitted before the first [AuraQueueEntry]. Keep in sync with the list. */
private const val AURA_QUEUE_LEADING_ITEMS = 2

/**
 * The lazy-list key of a queue row. One helper because the SAME string has to be the LazyColumn item
 * key, the [ReorderableItem] key and the value the reorder callback maps back to a queue index — if the
 * three ever disagreed, a drag would move the wrong song.
 */
private fun auraSongKey(window: Timeline.Window): String = "aura_queue_song_${window.uid.hashCode()}"

private sealed interface AuraQueueEntry {
    data class Label(val id: String, val text: String, val radio: Boolean) : AuraQueueEntry
    data class Song(
        val queueIndex: Int,
        val window: Timeline.Window,
        val radio: Boolean,
        val isCurrent: Boolean,
    ) : AuraQueueEntry
}

/**
 * Turns the play-ordered queue into the render's three blocks.
 *
 * `queueWindows` is in PLAY order (shuffle-aware) while `listQueueSize` is a TIMELINE boundary, so the
 * per-row test is `window.firstPeriodIndex < listQueueSize` — correct under shuffle too. A
 * `listQueueSize` of 0 means the engine has no list context for this queue (a directly started radio,
 * a mix, or a queue adopted from Android Auto): everything is then one block and no radio label is
 * drawn, because claiming a boundary we do not have would be a placebo.
 *
 * Pure function, no Compose state — it is called inside a `remember` keyed on its three inputs, so the
 * plan is rebuilt when the queue changes and never while scrolling.
 */
private fun buildAuraQueueEntries(
    windows: List<Timeline.Window>,
    listQueueSize: Int,
    currentIndex: Int,
): List<AuraQueueEntry> {
    if (windows.isEmpty()) return emptyList()
    val entries = ArrayList<AuraQueueEntry>(windows.size + 3)
    var emittedPlayed = false
    var emittedUpcoming = false
    var emittedRadio = false

    windows.forEachIndexed { index, window ->
        val isCurrent = index == currentIndex
        val radio = listQueueSize > 0 && window.firstPeriodIndex >= listQueueSize
        when {
            isCurrent -> Unit // the current song lives in the pinned "SONANDO" header, no label here
            index < currentIndex -> if (!emittedPlayed) {
                emittedPlayed = true
                entries += AuraQueueEntry.Label("played", "YA SONÓ", radio = false)
            }
            radio -> if (!emittedRadio) {
                emittedRadio = true
                entries += AuraQueueEntry.Label(
                    id = "radio",
                    text = if (emittedUpcoming) "DESPUÉS · RADIO INFINITA" else "A CONTINUACIÓN · RADIO INFINITA",
                    radio = true,
                )
            }
            else -> if (!emittedUpcoming) {
                emittedUpcoming = true
                entries += AuraQueueEntry.Label("list", "A CONTINUACIÓN · DE TU LISTA", radio = false)
            }
        }
        entries += AuraQueueEntry.Song(
            queueIndex = index,
            window = window,
            radio = radio,
            isCurrent = isCurrent,
        )
    }
    return entries
}
