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
import androidx.annotation.StringRes
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
import androidx.compose.foundation.focusGroup
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
import iad1tya.echo.music.constants.CropAlbumArtKey
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
import iad1tya.echo.music.playback.CastConnectionHandler
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.ui.utils.ShowMediaInfo
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.ui.utils.tvFocusableItem
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.utils.ShareLinks
import iad1tya.echo.music.utils.isLocalMediaId
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
    // Kept for the drop-in signature, and deliberately not read: "Negro puro (AMOLED)" now moves
    // [AuraPalette.Ground] itself, so this sheet goes black with every other new surface instead of
    // branching to its own flat black while the player behind it kept `#060A12`.
    @Suppress("UNUSED_PARAMETER") pureBlack: Boolean,
    showInlineLyrics: Boolean,
    modifier: Modifier = Modifier,
    // "Fondo del reproductor", resolved by the player and handed down. It used to carry
    // `@Suppress("UNUSED_PARAMETER")` — the queue accepted the style and ignored it. It now paints this
    // sheet's ground; see the [rememberAuraGround] call below.
    playerBackground: PlayerBackgroundStyle = PlayerBackgroundStyle.DEFAULT,
    onToggleLyrics: () -> Unit = {},
    /** Opens the full player menu (Más / Ajustes). Lives on the collapsed bar next to Letras. */
    onMore: () -> Unit = {},
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

    // Compartir / Ecualizador / Vídeo — relocated here from the player's own quick-access row (owner
    // request: keep only the four "qué opinas y qué guardas" buttons up there; these three join the
    // bar's "qué haces con ella" set). Same predicates, same actions as AuraPlayer.kt used before the
    // move — no second implementation, this is a relocation, not a rebuild.
    val videoMode by playerConnection.videoMode.collectAsState()
    val highPerfMode by rememberPreference(HighPerformanceModeKey, false)
    val (exportedVideoIds) = rememberPreference(ExportedVideoIdsKey, "")
    val isTvOrCarBar = iad1tya.echo.music.ui.utils.rememberIsTvOrCar()
    val hasVideo = mediaMetadata?.let { meta ->
        val exportedVideo = exportedVideoIds.split(',').any { it.trim() == meta.id }
        videoMode || (
            (!highPerfMode || isTvOrCarBar) &&
                (meta.isVideoSong || exportedVideo || !meta.podcastVideoUrl.isNullOrEmpty())
            )
    } ?: false
    val shareableLink = mediaMetadata?.let { !it.id.isLocalMediaId() } ?: false

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

    var showCommentSheet by rememberSaveable { mutableStateOf(false) }

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
            )
        }
    }

    // ── The queue's GROUND ────────────────────────────────────────────────────────────────────────
    // "Fondo del reproductor" owns it, exactly as it owns the player sheet's: the queue IS part of the
    // player surface, so the two must not disagree. [playerBackground] is the style the player resolved
    // and hands down — it used to arrive here and be ignored, which is what the `@Suppress(
    // "UNUSED_PARAMETER")` on that parameter was admitting. [rememberAuraGround] turns it into the same
    // layered recipe the player draws, dimmed to the .45 the render specifies for Cola.
    //
    // `pureBlack` is deliberately NOT a term any more. It used to branch this surface to a flat black
    // while the player kept its blue-black ground — which is exactly the reported bug: dragging the queue
    // up went pure black, dragging it down brought `#060A12` back, in one gesture. AMOLED now moves
    // [AuraPalette.Ground] itself (AuraPalette.kt), so this sheet, the player and every other new screen
    // go black together.
    val ground = rememberAuraGround(
        mediaId = mediaMetadata?.id,
        thumbnailUrl = mediaMetadata?.thumbnailUrl,
        styleOverride = playerBackground,
    )

    BottomSheet(
        state = state,
        modifier = modifier,
        background = { Box(Modifier.fillMaxSize().background(Color.Unspecified)) },
        collapsedContent = {
            AuraQueueBar(
                onOpenQueue = { state.expandSoft() },
                isBluetoothConnected = isBluetoothConnected,
                onAudioOutput = { showAudioDeviceBottomSheet = true },
                showInlineLyrics = showInlineLyrics,
                onToggleLyrics = onToggleLyrics,
                showCommentButton = showCommentButton,
                onComments = { showCommentSheet = true },
                shareEnabled = shareableLink,
                onShare = {
                    mediaMetadata?.let { meta ->
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, ShareLinks.song(meta.id))
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                },
                onEqualizer = {
                    // Collapse the PLAYER (not this queue sheet — AuraQueueBar is already the queue's
                    // collapsed content): same as the button did in AuraPlayer.kt before the move,
                    // matching AuraPlayerMenu.kt's own equalizer row.
                    playerBottomSheetState.collapseSoft()
                    navController.navigate("settings/equalizer") { launchSingleTop = true }
                },
                showVideoButton = hasVideo,
                videoModeActive = videoMode,
                onToggleVideo = { playerConnection.toggleVideoMode() },
                onMore = onMore,
                contentColor = textBackgroundColor,
                activeContainerColor = textButtonColor,
                activeContentColor = iconButtonColor,
            )

            if (showAudioDeviceBottomSheet) {
                AudioDeviceBottomSheet(onDismiss = { showAudioDeviceBottomSheet = false })
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

        // Does the queue continue by itself once the last song ends? Only then may the plan stay silent
        // about the end of the list — see [buildAuraQueueEntries]. Same two facts the autoplay block at
        // the bottom of this very list is built from, read once here.
        val hasAutoplayContinuation = autoLoadMore && automix.isNotEmpty()

        // The grouped row plan: computed once per (queue, boundary, current song, repeat mode, autoplay),
        // never per frame.
        val entries = remember(
            mutableQueueWindows.toList(),
            listQueueSize,
            currentWindowIndex,
            repeatMode,
            hasAutoplayContinuation,
        ) {
            buildAuraQueueEntries(
                windows = mutableQueueWindows.toList(),
                listQueueSize = listQueueSize,
                currentIndex = currentWindowIndex,
                repeatMode = repeatMode,
                hasAutoplayContinuation = hasAutoplayContinuation,
                // SONANDO header already shows the current song — never repeat it as REPRODUCIENDO.
                omitPinnedCurrent = true,
            )
        }

        // Land on the first upcoming block when the sheet opens — and ONLY then. The classic effect keys
        // on the (stable) list instance so it fires once per open; ours has to key on `entries`, which
        // changes on every track advance and every reorder, so the one-shot is explicit.
        var didInitialScroll by remember { mutableStateOf(false) }
        LaunchedEffect(entries) {
            if (didInitialScroll) return@LaunchedEffect
            val target = entries.indexOfFirst {
                it is AuraQueueEntry.Label && it.id != "played" && it.id != "repeat_one"
            }.takeIf { it >= 0 }
                ?: entries.indexOfFirst { it is AuraQueueEntry.Song }
            if (target >= 0) {
                lazyListState.scrollToItem(target + AURA_QUEUE_LEADING_ITEMS)
                didInitialScroll = true
            }
        }

        // ── The queue's GROUND ────────────────────────────────────────────────────────────────
        // Drawn INSIDE the sheet's content, not in its `background` slot: BottomSheet's background is
        // a fixed full-screen layer, so a ground put there would fade in over the whole window — the
        // player's transport included — while the queue was still sliding up. The content box is the
        // one that carries the sheet's translation, so the ground travels with the sheet, exactly as
        // the ground this replaces did.
        Box(modifier = Modifier.fillMaxSize()) {
            AuraGroundLayer(ground, intensity = 0.45f)
            Column(
                modifier = Modifier.fillMaxSize(),
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
                            text = stringResource(R.string.aura_queue_title),
                            style = AuraType.SheetTitle,
                            color = AuraPalette.OnGround,
                            maxLines = 1,
                            overflow = AuraDefaultOverflow,
                            modifier = Modifier.weight(1f),
                        )
                        AuraIconButton(
                            icon = AuraIcons.Shuffle,
                            contentDescription = stringResource(R.string.shuffle),
                            onClick = {
                                if (shuffleModeEnabled) {
                                    playerConnection.player.shuffleModeEnabled = false
                                } else {
                                    playerConnection.service.toggleShuffleOrReshuffle()
                                }
                            },
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
                        AuraSectionLabel(stringResource(R.string.aura_queue_section_playing))
                        Spacer(Modifier.height(AuraSpacing.SectionGap))
                        AuraRow(
                            title = mediaMetadata?.title.orEmpty(),
                            subtitle = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                            highlighted = true,
                            artwork = { AuraCover(url = mediaMetadata?.thumbnailUrl, seed = mediaMetadata?.id, size = 44.dp) },
                            trailing = {
                                val liked = currentSong?.song?.liked == true
                                // Always mark the SONANDO row; animate while audible, play glyph while buffering.
                                AuraPlayingBars(isPlaying = isPlaying)
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
                                    contentDescription = stringResource(R.string.cd_exit_selection),
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
                                    itemsIndexed(items = automix, key = { _, it -> it.mediaId }) { _, item ->
                                        AuraAutomixRow(
                                            item = item,
                                            isListenTogetherGuest = isListenTogetherGuest,
                                            isCasting = isCasting,
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
                                                text = stringResource(entry.textRes),
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
                                                                    contentDescription = stringResource(R.string.cd_reorder),
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
                                                                if (isActive) AuraPlayingBars(isPlaying = isPlaying)
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
                                                                // ONE implementation, shared with the wide
                                                                // player's live queue column — see
                                                                // [auraJumpToQueueWindow].
                                                                auraJumpToQueueWindow(
                                                                    playerConnection = playerConnection,
                                                                    castHandler = castHandler,
                                                                    isCasting = isCasting,
                                                                    castIsPlaying = castIsPlaying,
                                                                    isCurrent = index == currentWindowIndex,
                                                                    window = window,
                                                                )
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

                                    itemsIndexed(items = automix, key = { _, it -> "aura_automix_${it.mediaId}" }) { _, item ->
                                        Column {
                                            Spacer(Modifier.height(AuraSpacing.RowGap))
                                            AuraAutomixRow(
                                                item = item,
                                                isListenTogetherGuest = isListenTogetherGuest,
                                                isCasting = isCasting,
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
                            text = stringResource(
                                if (locked) R.string.aura_queue_footer_locked
                                else R.string.aura_queue_footer_swipe_to_remove
                            ),
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
 * The bar under the player transport. It carries the union of the two classic bars (§4.1 the new
 * design, §4.2 the old one) MINUS whatever the player already draws a few dp above it — most importantly
 * the audio-output picker, which today only exists in the old design and is the app's only way to choose
 * an output device, and which nothing else on this surface owns.
 *
 * Three things are deliberately NOT here because the player itself owns them while this bar is visible:
 * aleatorio and repetir (the transport row) and ⋮ más (the player header). See the comments at their
 * former sites in the body below.
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
    showInlineLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    showCommentButton: Boolean,
    onComments: () -> Unit,
    shareEnabled: Boolean,
    onShare: () -> Unit,
    onEqualizer: () -> Unit,
    showVideoButton: Boolean,
    videoModeActive: Boolean,
    onToggleVideo: () -> Unit,
    onMore: () -> Unit,
    contentColor: Color,
    activeContainerColor: Color,
    activeContentColor: Color,
) {
    Row(
        // Centred, not left-packed with a trailing ⋮: the bar is now four or five buttons of the same
        // rank, and it sits directly under the player's own centred transport and centred quick-access
        // row. Left-packing them and hanging one button off the right edge is what the removed ⋮ was
        // holding the layout in.
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
    ) {
        AuraBarButton(
            contentDescription = stringResource(R.string.cd_open_queue),
            active = false,
            onClick = onOpenQueue,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint -> AuraIconGlyph(AuraIcons.Queue, null, size = 21.dp, tint = tint) }

        // ⚠️ The app's ONLY audio-output picker. It used to be reachable only with the old player
        // design; here it is unconditional.
        AuraBarButton(
            contentDescription = stringResource(R.string.cd_audio_output),
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
            contentDescription = stringResource(R.string.queue_tab_lyrics),
            active = showInlineLyrics,
            onClick = onToggleLyrics,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint -> AuraIconGlyph(AuraIcons.Lyrics, null, size = 21.dp, tint = tint) }

        if (showCommentButton) {
            AuraBarButton(
                contentDescription = stringResource(R.string.comments),
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

        // Compartir / Ecualizador / Vídeo — relocated from the player's quick-access row (owner
        // request: "que los botones... solo sea una fila de cuatro y los otros me los pases en la
        // última fila de abajo"). Same actions AuraPlayer.kt called before the move.
        AuraBarButton(
            contentDescription = stringResource(R.string.share),
            active = false,
            enabled = shareEnabled,
            onClick = onShare,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint -> AuraIconGlyph(AuraIcons.Share, null, size = 21.dp, tint = tint) }

        AuraBarButton(
            contentDescription = stringResource(R.string.equalizer),
            active = false,
            onClick = onEqualizer,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
        ) { tint -> AuraIconGlyph(AuraIcons.Equalizer, null, size = 21.dp, tint = tint) }

        if (showVideoButton) {
            AuraBarButton(
                contentDescription = stringResource(if (videoModeActive) R.string.music else R.string.video),
                active = videoModeActive,
                onClick = onToggleVideo,
                contentColor = contentColor,
                activeContainerColor = activeContainerColor,
                activeContentColor = activeContentColor,
            ) { tint ->
                // Alternate glyph so the control reads as music↔video, not "video only".
                AuraIconGlyph(
                    if (videoModeActive) AuraIcons.Music else AuraIcons.Video,
                    null,
                    size = 21.dp,
                    tint = tint,
                )
            }
        }

        // ALEATORIO / REPETIR stay on the transport row only (no duplicates here).
        //
        // MÁS / AJUSTES — owner request: full player-menu door lives HERE (same row as Letras), not as a
        // lone ⋮ in the top header while lyrics are closed. Header ⋮ remains only for the lyrics menu
        // while inline lyrics are open. Queue sheet expanded still has its own header ⋮.
        AuraBarButton(
            contentDescription = stringResource(R.string.more_options),
            active = false,
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
 * The list's summary line — how much queue there is — and the Radio action. §4.5's header, minus the
 * claim it could not keep.
 *
 * It used to read *"Continuar reproduciendo"* over *"Siguiente en la cola"*, and it sits directly above
 * the first row of the list, which for a user on the first track of a queue is the song that is PLAYING.
 * That pair is what produced *«me sale que la siguiente es la misma que estoy reproduciendo»*. The two
 * lines are gone, not renamed: «cuál es la siguiente» is now answered where it belongs, by the
 * **A CONTINUACIÓN** heading that [buildAuraQueueEntries] starts at the real next song.
 *
 * What is left is two facts and one action, all of them about the whole queue and none of them about the
 * row underneath: the song count, the total duration and «Iniciar radio». Nothing is lost — the count and
 * the duration were already here, in the right-hand column this now replaces.
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
                text = pluralStringResource(R.plurals.n_song, songCount, songCount),
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            Text(
                text = makeTimeString(totalDurationMs),
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
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

/**
 * A related/automix row: play-next and add-to-queue (service pool ops, zero network) + long-press menu.
 *
 * **Tapping the row PLAYS the song.** It used to pass a literal empty lambda while [AuraRow] still attached
 * `combinedClickable` (it does whenever `onLongClick` is non-null), so the row rippled, felt tappable and
 * did nothing — the classic list has the same dead tap (`Queue.kt:1641`), and it is a placebo either way.
 *
 * It cannot be a `seekToDefaultPosition`: an automix item is NOT in the player timeline at all — it lives in
 * `MusicService.automixItems`, a pool the service tops up (MusicService.kt:748) and only drains through
 * [iad1tya.echo.music.playback.MusicService.playNextAutomix] / `addToQueueAutomix`. So the tap does what the
 * "reproducir a continuación" button next to it does — remove from the pool, insert at
 * `currentMediaItemIndex + 1`, shuffle-order aware — and then steps onto it. `seekToNext()` is what makes
 * that land on the tapped song under shuffle too, because `playNext` rewrote the shuffle order so the
 * inserted block IS next.
 *
 * Both pool operations take a POSITION and `removeAt` it. This row deliberately does NOT hold on to the
 * position it was composed with: the pool is a live `StateFlow` that something else can drain between
 * composition and the tap — both players run an effect that consumes `automix[0]` as soon as the queue runs
 * out of next song (AuraPlayer.kt, `Player.kt:550`) — so a captured index can point at a different song, or
 * past the end of a shrunken list, which `removeAt` answers with an exception. The position is resolved by
 * identity at CLICK time instead, and a row whose song has already left the pool does nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuraAutomixRow(
    item: MediaItem,
    isListenTogetherGuest: Boolean,
    isCasting: Boolean,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val metadata = item.metadata ?: return

    // Not a composable read: it is only ever called from a click handler, on the live pool.
    fun poolPosition(): Int =
        playerConnection.service.automixItems.value.indexOfFirst { it.mediaId == item.mediaId }

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
                    contentDescription = stringResource(R.string.play_next),
                    onClick = {
                        val at = poolPosition()
                        if (at >= 0) playerConnection.service.playNextAutomix(item, at)
                    },
                    size = 20.dp,
                    tint = AuraPalette.OnGroundMuted,
                )
                AuraIconButton(
                    icon = AuraIcons.Queue,
                    contentDescription = stringResource(R.string.add_to_queue),
                    onClick = {
                        val at = poolPosition()
                        if (at >= 0) playerConnection.service.addToQueueAutomix(item, at)
                    },
                    size = 20.dp,
                    tint = AuraPalette.OnGroundMuted,
                )
            }
        },
        // Same guest rule the queue rows use (`else if (!isListenTogetherGuest)`, :993): a guest does not
        // steer the room's queue. `playWhenReady` mirrors the queue row too — and is skipped while casting,
        // where playback belongs to the other device and `playNext` itself already declines to start it.
        onClick = {
            val at = if (isListenTogetherGuest) -1 else poolPosition()
            if (at >= 0) {
                playerConnection.service.playNextAutomix(item, at)
                playerConnection.player.seekToNext()
                if (!isCasting) playerConnection.player.playWhenReady = true
            }
        },
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

/**
 * Cover art with the palette's deterministic gradient placeholder underneath while it loads.
 *
 * Honours "Recortar las portadas" ([CropAlbumArtKey], default OFF) like every classic renderer
 * (`Items.kt:1371/1453/1546`, `Thumbnail.kt:341`). Hard-coding `ContentScale.Crop` here was one of the
 * three places the beta ignored that switch.
 */
@Composable
private fun AuraCover(url: String?, seed: String?, size: Dp) {
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    AuraArtwork(size = size, placeholderSeed = seed) {
        AuraStableCoverImage(
            url = url,
            contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
            decodeTo = 128,
            seed = seed,
            modifier = Modifier.fillMaxSize(),
        )
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
    /**
     * [id] is a stable LIST KEY, not text: it is what `"aura_queue_label_$id"` and
     * `"aura_wide_label_$id"` are built from, so two labels must never share one and it must never be
     * localised. The words the user reads are [textRes], resolved at draw time — [buildAuraQueueEntries]
     * is a pure function with no Compose scope, so it cannot resolve them itself.
     */
    data class Label(val id: String, @StringRes val textRes: Int, val radio: Boolean) : AuraQueueEntry
    data class Song(
        val queueIndex: Int,
        val window: Timeline.Window,
        val radio: Boolean,
        val isCurrent: Boolean,
    ) : AuraQueueEntry
}

/** Id of the heading that sits directly above the current song. Also the scroll-to-current target. */
private const val AURA_QUEUE_LABEL_CURRENT = "current"

/**
 * Turns the play-ordered queue into the render's blocks.
 *
 * ## «La siguiente es la misma que estoy reproduciendo»
 * The reported bug. The list used to emit NO heading for the current song — the comment here said it
 * "lives in the pinned SONANDO header" — while [AuraQueueListHeader], two dp above the first row, read
 * *"Continuar reproduciendo / Siguiente en la cola"*. When the user is on the first track of a queue,
 * `currentIndex` is 0, so the first row under that promise **is** the song that is playing. The header
 * was a promise about a row it did not describe.
 *
 * The fix is structural, not a relabel: the current song now gets its own **REPRODUCIENDO** heading, and
 * the up-next headings ("A CONTINUACIÓN · DE TU LISTA" / "· RADIO INFINITA") therefore start at the song
 * that really is next. Nothing is removed from the list — the current song keeps its row, so it can still
 * be dragged, swiped away, selected and long-pressed, and the select-all count still compares against the
 * whole queue.
 *
 * ## What "next" means
 * `windows` is in PLAY order, not list order: it is built by walking `getNextWindowIndex` /
 * `getPreviousWindowIndex` with the player's own `shuffleModeEnabled` (`PlayerExt.kt:33`), so with
 * ALEATORIO on, `windows[currentIndex + 1]` is the song the player will really play next. There is no
 * separate shuffle path here, and there must not be one.
 *
 * `listQueueSize` is a TIMELINE boundary, so the per-row radio test is
 * `window.firstPeriodIndex >= listQueueSize` — correct under shuffle too. A `listQueueSize` of 0 means the
 * engine has no list context for this queue (a directly started radio, a mix, or a queue adopted from
 * Android Auto): everything is then one block and no radio label is drawn, because claiming a boundary we
 * do not have would be a placebo.
 *
 * ## El final de la cola
 * [repeatMode] and [hasAutoplayContinuation] are read so the end of the list states what the engine will
 * actually do instead of just stopping:
 *  · **Repetir una** — auto-advance replays THIS song, so a note says so directly under it. The up-next
 *    block still appears when there are songs after it, because «siguiente» still skips to them.
 *  · **Repetir todo** on the last song — the player wraps to `windows[0]`. That is stated as a heading
 *    and NOT as a row: the row for `windows[0]` already exists further up under "YA SONÓ", and a second
 *    row for the same window would be a duplicate lazy-list key, i.e. a crash.
 *  · **Nothing after, no repeat, no autoplay** — "FIN DE LA COLA". When autoplay WILL continue the queue
 *    the label is omitted, because the autoplay block below the list already shows the real songs and
 *    announcing an end that is not one would be the same class of lie this function just fixed.
 *
 * Pure function, no Compose state — called inside a `remember` keyed on its inputs, so the plan is
 * rebuilt when the queue changes and never while scrolling.
 */
private fun buildAuraQueueEntries(
    windows: List<Timeline.Window>,
    listQueueSize: Int,
    currentIndex: Int,
    repeatMode: Int,
    hasAutoplayContinuation: Boolean,
    /**
     * When true (queue sheet with pinned SONANDO header), the current song is NOT emitted again under
     * a REPRODUCIENDO label — that duplication is what the owner rejected. Wide queue keeps the row.
     */
    omitPinnedCurrent: Boolean = false,
): List<AuraQueueEntry> {
    if (windows.isEmpty()) return emptyList()
    val entries = ArrayList<AuraQueueEntry>(windows.size + 5)
    var emittedPlayed = false
    var emittedUpcoming = false
    var emittedRadio = false

    windows.forEachIndexed { index, window ->
        val isCurrent = index == currentIndex
        val radio = listQueueSize > 0 && window.firstPeriodIndex >= listQueueSize
        when {
            isCurrent && omitPinnedCurrent -> {
                // Current lives only in the SONANDO header. Still announce repeat-one under the list
                // so the engine's loop is not silent.
                if (repeatMode == Player.REPEAT_MODE_ONE) {
                    entries += AuraQueueEntry.Label(
                        "repeat_one",
                        R.string.aura_queue_label_repeat_one,
                        radio = false,
                    )
                }
            }
            isCurrent -> {
                entries += AuraQueueEntry.Label(
                    id = AURA_QUEUE_LABEL_CURRENT,
                    textRes = R.string.aura_queue_label_now_playing,
                    radio = false,
                )
                entries += AuraQueueEntry.Song(
                    queueIndex = index,
                    window = window,
                    radio = radio,
                    isCurrent = true,
                )
                if (repeatMode == Player.REPEAT_MODE_ONE) {
                    entries += AuraQueueEntry.Label(
                        "repeat_one",
                        R.string.aura_queue_label_repeat_one,
                        radio = false,
                    )
                }
            }
            index < currentIndex -> if (!emittedPlayed) {
                emittedPlayed = true
                entries += AuraQueueEntry.Label("played", R.string.aura_queue_label_played, radio = false)
                entries += AuraQueueEntry.Song(
                    queueIndex = index,
                    window = window,
                    radio = radio,
                    isCurrent = false,
                )
            } else {
                entries += AuraQueueEntry.Song(
                    queueIndex = index,
                    window = window,
                    radio = radio,
                    isCurrent = false,
                )
            }
            radio -> {
                if (!emittedRadio) {
                    emittedRadio = true
                    entries += AuraQueueEntry.Label(
                        id = "radio",
                        textRes = if (emittedUpcoming) R.string.aura_queue_label_radio_later
                        else R.string.aura_queue_label_radio_next,
                        radio = true,
                    )
                }
                entries += AuraQueueEntry.Song(
                    queueIndex = index,
                    window = window,
                    radio = radio,
                    isCurrent = false,
                )
            }
            else -> {
                if (!emittedUpcoming) {
                    emittedUpcoming = true
                    entries += AuraQueueEntry.Label("list", R.string.aura_queue_label_list_next, radio = false)
                }
                entries += AuraQueueEntry.Song(
                    queueIndex = index,
                    window = window,
                    radio = radio,
                    isCurrent = false,
                )
            }
        }
    }

    // The tail only makes a claim when the current song really is the last one in play order.
    if (currentIndex in windows.indices && currentIndex == windows.lastIndex) {
        when {
            repeatMode == Player.REPEAT_MODE_ONE -> Unit // already stated under the row itself
            repeatMode == Player.REPEAT_MODE_ALL && windows.size > 1 ->
                entries += AuraQueueEntry.Label("wrap", R.string.aura_queue_label_wrap, radio = false)
            // «Repetir todo» over a queue of ONE song behaves exactly like «repetir una»: it loops that
            // song. Saying "vuelve al principio" would point at a row that is this same row, and saying
            // "fin de la cola" would be flatly false — the player is not going to stop.
            repeatMode == Player.REPEAT_MODE_ALL ->
                entries += AuraQueueEntry.Label("repeat_all_single", R.string.aura_queue_label_repeat_one, radio = false)
            !hasAutoplayContinuation ->
                entries += AuraQueueEntry.Label("end", R.string.aura_queue_label_end, radio = false)
        }
    }
    return entries
}

// ── La cola del reproductor ancho ─────────────────────────────────────────────────────────────────

/**
 * "Tocar una fila de la cola": jump to that song, or play/pause when it is already the current one.
 *
 * ONE implementation, called by the queue sheet's rows above AND by [AuraWideQueuePane], the wide
 * player's live queue column. The classic wide player keeps its own copy of this
 * (`LandscapeQueuePane`'s onClick, `ui/player/Player.kt:3575`) and that copy does **not** know about
 * Cast — tapping a row there while casting seeks the local player. Sharing one function inside the new
 * UI means the wide column and the sheet cannot drift, and the column inherits the Cast branch.
 *
 * Not `@Composable`: it takes already-resolved state so it can be called from any click lambda.
 */
private fun auraJumpToQueueWindow(
    playerConnection: PlayerConnection,
    castHandler: CastConnectionHandler?,
    isCasting: Boolean,
    castIsPlaying: Boolean,
    isCurrent: Boolean,
    window: Timeline.Window,
) {
    if (isCurrent) {
        if (isCasting) {
            if (castIsPlaying) castHandler?.pause() else castHandler?.play()
        } else {
            playerConnection.togglePlayPause()
        }
        return
    }
    if (isCasting) {
        val navigated = castHandler?.navigateToMediaIfInQueue(window.mediaItem.mediaId) ?: false
        if (!navigated) {
            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
        }
    } else {
        playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
        playerConnection.player.playWhenReady = true
    }
}

/**
 * The **live queue column** of the wide player — §2.10's «Fila de la cola → salta a esa canción», in the
 * redesign's language.
 *
 * ## Why a column and not the portrait sheet turned sideways
 * The classic wide player does not squeeze its bottom sheet into the landscape; it puts the queue on the
 * LEFT as a permanently visible list and the now-playing pane on the right (`Player.kt:3025`). That is
 * the interaction model this reproduces: no gesture to learn, the next songs always readable, one tap to
 * jump. The queue SHEET still exists underneath in the wide shape exactly as it does in the classic one
 * (its collapsed bar is what carries cola / salida de audio / temporizador / letra), so everything the sheet
 * owns — reordenar, deslizar para quitar, selección múltiple, las pestañas LETRA y RELACIONADOS — is
 * still one tap away. This column is the *glanceable* half, not a replacement.
 *
 * ## What it reuses
 * [buildAuraQueueEntries] (so "A CONTINUACIÓN · DE TU LISTA" / "DESPUÉS · RADIO INFINITA" appear here
 * too — the classic wide queue has no such split), [AuraRow], [AuraCover], [AuraPlayingBars] and
 * [auraJumpToQueueWindow]. Nothing here is a second copy of anything.
 *
 * ## TV / coche
 * `tvFocusRestorer().focusGroup()` on the list — the same pair the classic `LandscapeQueuePane` uses —
 * so the D-pad enters and leaves the column cleanly and the ring survives scrolling a row out of
 * composition. Every row carries [tvFocusableItem], so the ring lights the whole row.
 *
 * A row has **no ⋮** on purpose, matching the classic wide queue: one focus stop per song keeps D-pad
 * traversal down a long queue predictable, and the per-song menu lives in the sheet.
 *
 * ## Thermal / battery
 * A `LazyColumn` (only visible rows composed), the same static "sonando" bars as the sheet, one
 * `animateScrollToItem` per track change. No ticker, no per-frame work.
 */
@Composable
internal fun AuraWideQueuePane(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isTvOrCar = iad1tya.echo.music.ui.utils.rememberIsTvOrCar()

    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val listQueueSize by playerConnection.listQueueSize.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    // Same two terms the sheet feeds [buildAuraQueueEntries]: this column draws the SAME plan, so it must
    // answer «¿y después?» with the same facts instead of trailing off at the last row.
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val automix by playerConnection.service.automixItems.collectAsState()
    val (autoLoadMore) = rememberPreference(AutoLoadMoreKey, defaultValue = true)

    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST

    val castHandler = remember(playerConnection) {
        runCatching { playerConnection.service.castConnectionHandler }.getOrNull()
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsState() ?: remember { mutableStateOf(false) }

    val hasAutoplayContinuation = autoLoadMore && automix.isNotEmpty()
    val entries = remember(
        queueWindows,
        listQueueSize,
        currentWindowIndex,
        repeatMode,
        hasAutoplayContinuation,
    ) {
        buildAuraQueueEntries(
            windows = queueWindows,
            listQueueSize = listQueueSize,
            currentIndex = currentWindowIndex,
            repeatMode = repeatMode,
            hasAutoplayContinuation = hasAutoplayContinuation,
        )
    }

    val lazyState = rememberLazyListState()
    // Follow the track, exactly as the classic wide queue does (Player.kt:3548). Keyed on the CURRENT
    // INDEX and the queue length, so it runs once per track change and never while scrolling.
    //
    // Targets the REPRODUCIENDO heading rather than the row, for the same reason the sheet does: scrolling
    // the row to the top edge would push its own heading out of view.
    LaunchedEffect(currentWindowIndex, entries.size) {
        val headingIndex = entries.indexOfFirst {
            it is AuraQueueEntry.Label && it.id == AURA_QUEUE_LABEL_CURRENT
        }
        val target = if (headingIndex >= 0) headingIndex
        else entries.indexOfFirst { it is AuraQueueEntry.Song && it.isCurrent }
        if (target >= 0) runCatching { lazyState.animateScrollToItem(target) }
    }

    LazyColumn(
        state = lazyState,
        modifier = modifier.tvFocusRestorer().focusGroup(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(
            items = entries,
            key = { _, entry ->
                when (entry) {
                    is AuraQueueEntry.Label -> "aura_wide_label_${entry.id}"
                    is AuraQueueEntry.Song -> "aura_wide_${auraSongKey(entry.window)}"
                }
            },
        ) { _, entry ->
            when (entry) {
                is AuraQueueEntry.Label -> Column {
                    Spacer(Modifier.height(AuraSpacing.SectionTop))
                    AuraSectionLabel(
                        text = stringResource(entry.textRes),
                        color = if (entry.radio) AuraPalette.Violet.copy(alpha = 0.85f)
                        else AuraPalette.OnGroundFaint,
                    )
                    Spacer(Modifier.height(AuraSpacing.SectionGap))
                }

                is AuraQueueEntry.Song -> {
                    val window = entry.window
                    val meta = window.mediaItem.metadata
                    if (meta != null) {
                        AuraRow(
                            modifier = Modifier.tvFocusableItem(isTvOrCar, AuraShapes.Highlight),
                            title = meta.title,
                            subtitle = meta.artists.joinToString { it.name },
                            highlighted = entry.isCurrent,
                            dimmed = entry.radio && !entry.isCurrent,
                            artwork = {
                                AuraCover(
                                    url = meta.thumbnailUrl,
                                    seed = window.mediaItem.mediaId,
                                    size = 44.dp,
                                )
                            },
                            trailing = {
                                if (entry.isCurrent) {
                                    AuraPlayingBars(isPlaying = isPlaying)
                                } else {
                                    AuraTechnicalText(text = makeTimeString(meta.duration * 1000L))
                                }
                            },
                            onClick = {
                                // A Listen Together GUEST does not steer the room's playback — the same
                                // guard the sheet's rows carry.
                                if (!isListenTogetherGuest) {
                                    auraJumpToQueueWindow(
                                        playerConnection = playerConnection,
                                        castHandler = castHandler,
                                        isCasting = isCasting,
                                        castIsPlaying = castIsPlaying,
                                        isCurrent = entry.isCurrent,
                                        window = window,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
