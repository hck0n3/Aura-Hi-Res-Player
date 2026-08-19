package iad1tya.echo.music.ui.screens.equalizer.axion

import androidx.compose.animation.*
import android.content.Context
import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import iad1tya.echo.music.R
import androidx.compose.foundation.shape.RoundedCornerShape
import iad1tya.echo.music.eq.audio.CustomEqualizerAudioProcessor
import iad1tya.echo.music.eq.audio.SuperpoweredEngineStatus
import iad1tya.echo.music.eq.audio.headroomPreampDb
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.EqMode
import iad1tya.echo.music.eq.data.FactoryPreset
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import iad1tya.echo.music.eq.data.SavedEQProfile
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.constants.EqFftMeterEnabledKey
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.constants.SafeVolumeEnabledKey
import iad1tya.echo.music.constants.SpatialAudioEnabledKey
import iad1tya.echo.music.constants.SpatialAudioProfileKey
import iad1tya.echo.music.constants.TidalSimulationEnabledKey
import iad1tya.echo.music.eq.audio.SpatialAudioProfile
import iad1tya.echo.music.ui.newui.AuraDialogWindowEffects
import iad1tya.echo.music.ui.newui.AuraFloatingSurface
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.LocalAuraFloatingChrome
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.utils.rememberIsWideLayout
import iad1tya.echo.music.utils.DeviceCapabilities
import iad1tya.echo.music.utils.DeviceTier
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.rememberEnumPreference
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlin.math.sin
import kotlin.math.sqrt
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AxionEqScreen(
    onBackClick: () -> Unit,
    onAutoEqClick: () -> Unit = {},
    viewModel: AxionEqViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsState()
    val bandGains by viewModel.bandGains.collectAsState()
    val preamp by viewModel.preamp.collectAsState()
    val autoEqActive by viewModel.autoEqActive.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val customProfiles by viewModel.customProfiles.collectAsState()
    val eqMode by viewModel.eqMode.collectAsState()
    val peqBands by viewModel.peqBands.collectAsState()

    // Auto-EQ now runs as its OWN cascaded correction stage; the manual EQ stacks ON TOP and stays fully
    // editable while Auto-EQ is active (no lock). So the graphic/parametric editor follows the EQ on/off only.
    val graphicEnabled = enabled

    var showSaveDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    if (showDeviceDialog) {
        DeviceEqDialog(
            customProfiles = customProfiles,
            onDismiss = { showDeviceDialog = false },
        )
    }
    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                viewModel.saveCustomProfile(name)
                showSaveDialog = false
            },
        )
    }
    if (showManageDialog) {
        ManagePresetsDialog(
            customProfiles = customProfiles,
            onDismiss = { showManageDialog = false },
            onDeleteSelected = { ids ->
                viewModel.deleteProfiles(ids)
                showManageDialog = false
            },
        )
    }

    // Visual-only pass, explicitly requested and scoped: page background + TopAppBar chrome match the
    // "Interfaz nueva" ground, exactly like every other settings screen. Nothing below this — bands,
    // curve, presets, the DSP engine banner — is touched; see eq/ and app/src/main/cpp/ (never touch
    // without being asked) and the one prior authorized exception (#49, layout-only).
    val skin = rememberAuraPanelSkin()
    val ground = if (skin.enabled && skin.darkGround) AuraPalette.Ground else MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = ground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.echo_equalizer)) },
                navigationIcon = {
                    iad1tya.echo.music.ui.component.IconButton(onClick = onBackClick, onLongClick = null) {
                        Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ground,
                    scrolledContainerColor = if (skin.enabled && skin.darkGround)
                        AuraPalette.GroundRaised
                    else
                        MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        // #49 — the EQ has to GROW with the screen instead of being squeezed into a fixed strip.
        //
        // This used to fork into a two-column Row above a hardcoded 840dp. That branch was actively WORSE
        // than the narrow one: its RIGHT column only ever contained `Spacer(height = 60.dp)` (the "DSP effect
        // switches" its comment promised were never wired up), so on a big screen the EQ was crushed into
        // half the width with the other half BLANK. Two ways out: fill the right column with real content, or
        // drop the split for ONE centred column. Chosen: the single column, because (1) filling the second
        // pane means INVENTING new UI and re-homing controls — new layout, and the request here is sizing;
        // (2) [EqMainContent] is already a `ColumnScope` extension written for exactly one scrolling column,
        // so this is its intended contract, not a workaround; (3) it DELETES a whole duplicated code path
        // rather than adding one. The bands, curve and PEQ graph below now expand into the extra width, which
        // is what "expand with the screen" actually meant.
        //
        // The 840dp literal is gone: the gate is [rememberIsWideLayout] (700dp, the shared breakpoint that
        // actually fires on an unfolded foldable). It is a LAYOUT gate — never [rememberIsTvOrCar], which
        // would switch on the TV/car D-pad ring on a wide phone (registry #12/#26).
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideLayout = rememberIsWideLayout()
            // Cap the readable measure so a ~900dp+ window doesn't stretch every row edge to edge (#50,
            // "todo lo pone gigante"). Below the cap this is the full width — i.e. phones are unchanged.
            val contentWidth = if (isWideLayout) maxWidth.coerceAtMost(EQ_MAX_CONTENT_WIDTH) else maxWidth
            // While a band/preamp slider is dragged, freeze page scroll so the chrome does not ride
            // with the finger (owner: "la interfaz se mueve con la barra").
            var sliderDragActive by remember { mutableStateOf(false) }
            val pageScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .width(contentWidth)
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .verticalScroll(pageScroll, enabled = !sliderDragActive)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EngineUnavailableBanner()
                EqMainContent(
                    skin = skin,
                    viewModel = viewModel,
                    enabled = enabled,
                    graphicEnabled = graphicEnabled,
                    bandGains = bandGains,
                    preamp = preamp,
                    autoEqActive = autoEqActive,
                    isDirty = isDirty,
                    customProfiles = customProfiles,
                    eqMode = eqMode,
                    peqBands = peqBands,
                    onSaveClick = { showSaveDialog = true },
                    onManageClick = { showManageDialog = true },
                    onDeviceClick = { showDeviceDialog = true },
                    onAutoEqClick = onAutoEqClick,
                    onSliderDragActiveChange = { sliderDragActive = it },
                )
            }
        }
    }
}

/**
 * Tells the user, on the EQ screen itself, when the native Superpowered engine is not actually
 * processing audio — so the sliders and the response curve are never a placebo.
 *
 * The Superpowered SDK can stop working without any warning to us: their agreement lets them disable a
 * non-compliant licence key "with or without notice", and `Superpowered::Initialize` returns void, so
 * nothing in the API would tell us. `CustomEqualizerAudioProcessor` therefore proves the DSP is alive
 * empirically and publishes the verdict here.
 *
 * Deliberately silent while the status is UNKNOWN (no audio configured yet) or HEALTHY — a banner that
 * appears on a working install would be worse than no banner at all.
 */
@Composable
private fun EngineUnavailableBanner() {
    val status by CustomEqualizerAudioProcessor.engineStatus.collectAsState()
    if (status != SuperpoweredEngineStatus.DEGRADED && status != SuperpoweredEngineStatus.UNAVAILABLE) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "El ecualizador no está sonando",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "El motor de audio no se está aplicando en este dispositivo, así que mover estas " +
                    "bandas o el audio espacial no cambia nada de lo que oyes. Preferimos decírtelo a " +
                    "dejarte un control que no hace nada. Envía el registro desde Ajustes ▸ Registros " +
                    "para que podamos verlo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** Widest the EQ content column is allowed to get before it stops being readable and starts being stretched. */
private val EQ_MAX_CONTENT_WIDTH = 900.dp

/** Narrow-screen width of one band column — also the FLOOR below which the band row keeps scrolling (#49b). */
private val BAND_SLIDER_MIN_WIDTH = 46.dp

/** Gap between band columns. */
private val BAND_SLIDER_SPACING = 2.dp

/** Inner horizontal padding of the band row. */
private val BAND_ROW_HORIZONTAL_PADDING = 12.dp

/** Bars drawn in the live FFT meter. */
private const val EQ_FFT_BAR_COUNT = 24

/** Normalized level at which the traffic-light meter turns amber. */
private const val EQ_FFT_AMBER_THRESHOLD = 0.55f

/** Normalized level at which the traffic-light meter turns cyan (saturation). */
private const val EQ_FFT_SATURATE_THRESHOLD = 0.82f

private val EqFftGreen = Color(0xFF5AD68A)
private val EqFftAmber = Color(0xFFFFB74D)

private fun eqFftMeterDefaultEnabled(context: Context, highPerf: Boolean): Boolean =
    !highPerf && DeviceCapabilities.tier(context) != DeviceTier.LOW

private fun eqFftTrafficColor(level: Float, saturateColor: Color): Color = when {
    level >= EQ_FFT_SATURATE_THRESHOLD -> saturateColor
    level >= EQ_FFT_AMBER_THRESHOLD -> EqFftAmber
    else -> EqFftGreen
}

private data class EqFftSnapshot(
    val bars: FloatArray = FloatArray(EQ_FFT_BAR_COUNT),
    val peak: Float = 0f,
    val hasSignal: Boolean = false,
) {
    companion object {
        val Zero = EqFftSnapshot()
    }
}

/**
 * Live FFT spectrum for the EQ screen — Android [Visualizer] tap only; does not touch Superpowered or
 * the gain path. Smoothed once per frame like [rememberAuraRhythmLevel], but with FFT capture enabled.
 *
 * [generation] forces a full release/reattach even when [audioSessionId] is unchanged (track transitions
 * often reuse the session and leave a dead Visualizer until the user leaves EQ).
 */
@Composable
private fun rememberEqFftMeter(
    audioSessionId: Int,
    enabled: Boolean,
    playing: Boolean,
    generation: Int,
    onForceRebind: () -> Unit,
): State<EqFftSnapshot> {
    val snapshot = remember { mutableStateOf(EqFftSnapshot.Zero) }
    var rawBars by remember { mutableStateOf(FloatArray(EQ_FFT_BAR_COUNT)) }
    var rawPeak by remember { mutableFloatStateOf(0f) }
    var captureOk by remember { mutableStateOf(false) }
    var lastFftAtMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(enabled) {
        if (enabled) {
            iad1tya.echo.music.ui.newui.AuraVisualizerExclusive.setEqFftActive(true)
        }
        onDispose {
            iad1tya.echo.music.ui.newui.AuraVisualizerExclusive.setEqFftActive(false)
        }
    }

    DisposableEffect(audioSessionId, enabled, generation) {
        if (!enabled || audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            rawBars = FloatArray(EQ_FFT_BAR_COUNT)
            rawPeak = 0f
            captureOk = false
            onDispose { }
        } else {
            val attachScope = CoroutineScope(Dispatchers.Main.immediate)
            // Atomic hold so a cancel mid-attach never leaks a live Visualizer (Android allows only
            // one per session — a leak is the usual reason the meter stays on "Sin señal").
            val held = AtomicReference<Visualizer?>(null)
            fun releaseHeld() {
                held.getAndSet(null)?.let { v ->
                    runCatching {
                        v.setEnabled(false)
                        v.release()
                    }
                }
            }
            fun attachTo(session: Int): Visualizer? = runCatching {
                Visualizer(session).apply {
                    val range = Visualizer.getCaptureSizeRange()
                    captureSize = range[1].coerceAtLeast(range[0])
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        runCatching { setScalingMode(Visualizer.SCALING_MODE_NORMALIZED) }
                    }
                    val maxRate = Visualizer.getMaxCaptureRate()
                    val target = min(20_000, (maxRate / 2).coerceAtLeast(4_000))
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int,
                            ) = Unit

                            override fun onFftDataCapture(
                                visualizer: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int,
                            ) {
                                if (fft == null || fft.size < 4) return
                                lastFftAtMs = System.currentTimeMillis()
                                val binCount = fft.size / 2
                                var peak = 0f
                                val bars = FloatArray(EQ_FFT_BAR_COUNT)
                                for (b in 0 until EQ_FFT_BAR_COUNT) {
                                    val t0 = b.toDouble() / EQ_FFT_BAR_COUNT
                                    val t1 = (b + 1).toDouble() / EQ_FFT_BAR_COUNT
                                    // Log-ish spacing across bins (skip DC at 0).
                                    val start = (1 + t0 * t0 * (binCount - 2)).toInt().coerceIn(1, binCount - 1)
                                    val end = (1 + t1 * t1 * (binCount - 2)).toInt().coerceIn(start + 1, binCount)
                                    var sum = 0.0
                                    var count = 0
                                    for (i in start until end) {
                                        val re = fft.getOrNull(i * 2)?.toInt() ?: continue
                                        val im = fft.getOrNull(i * 2 + 1)?.toInt() ?: 0
                                        val mag = hypot(re.toDouble(), im.toDouble()).toFloat()
                                        sum += mag
                                        count++
                                        if (mag > peak) peak = mag
                                    }
                                    // Visualizer FFT mags are typically well below 128 on real content —
                                    // boost so the traffic-light meter actually moves.
                                    val avg = if (count > 0) (sum / count).toFloat() else 0f
                                    bars[b] = (avg / 48f).coerceIn(0f, 1f)
                                }
                                rawBars = bars
                                rawPeak = (peak / 48f).coerceIn(0f, 1f)
                            }
                        },
                        target,
                        false,
                        true,
                    )
                    setEnabled(true)
                }
            }.getOrNull()

            val attachJob = attachScope.launch {
                // Let AuraRhythm release its Visualizer after setEqFftActive(true), then settle ExoPlayer.
                delay(350)
                if (!isActive) return@launch
                val sessions = linkedSetOf(audioSessionId, 0).filter { it >= 0 }
                var ok = false
                for (attempt in 0 until 5) {
                    if (!isActive) return@launch
                    for (session in sessions) {
                        releaseHeld()
                        val v = attachTo(session) ?: continue
                        held.set(v)
                        ok = true
                        break
                    }
                    if (ok) break
                    delay(200L * (attempt + 1))
                }
                captureOk = ok
                lastFftAtMs = System.currentTimeMillis()
            }
            onDispose {
                attachJob.cancel()
                releaseHeld()
                rawBars = FloatArray(EQ_FFT_BAR_COUNT)
                rawPeak = 0f
                captureOk = false
            }
        }
    }

    val latestBars = rememberUpdatedState(rawBars)
    val latestPeak = rememberUpdatedState(rawPeak)
    val latestCaptureOk = rememberUpdatedState(captureOk)
    val latestLastFft = rememberUpdatedState(lastFftAtMs)
    val latestOnForceRebind = rememberUpdatedState(onForceRebind)

    // Watchdog: after a track change the Visualizer can go silent with the same session id.
    var lastForcedRebindMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(enabled, playing, audioSessionId, generation) {
        if (!enabled) return@LaunchedEffect
        while (isActive) {
            delay(1_200)
            if (!playing) continue
            val silentFor = System.currentTimeMillis() - latestLastFft.value
            if (silentFor > 2_000L) {
                val now = System.currentTimeMillis()
                if (now - lastForcedRebindMs >= 3_000L) {
                    lastForcedRebindMs = now
                    latestOnForceRebind.value()
                }
            }
        }
    }

    LaunchedEffect(enabled, playing) {
        if (!enabled) {
            snapshot.value = EqFftSnapshot.Zero
            return@LaunchedEffect
        }
        val smoothed = FloatArray(EQ_FFT_BAR_COUNT)
        var smoothedPeak = 0f
        while (isActive) {
            withFrameNanos {
                val targetPeak = if (playing) latestPeak.value else smoothedPeak * 0.92f
                smoothedPeak += (targetPeak - smoothedPeak) * if (targetPeak > smoothedPeak) 0.4f else 0.15f
                val targets = if (playing) latestBars.value else FloatArray(EQ_FFT_BAR_COUNT) { i ->
                    smoothed[i] * 0.92f
                }
                for (i in smoothed.indices) {
                    val t = targets.getOrElse(i) { 0f }
                    smoothed[i] += (t - smoothed[i]) * if (t > smoothed[i]) 0.45f else 0.18f
                }
                snapshot.value = EqFftSnapshot(
                    bars = smoothed.copyOf(),
                    peak = smoothedPeak.coerceIn(0f, 1f),
                    hasSignal = latestCaptureOk.value &&
                        (System.currentTimeMillis() - latestLastFft.value) < 2_500L,
                )
            }
        }
    }

    return snapshot
}

@Composable
private fun EqFftMeter(
    snapshot: EqFftSnapshot,
    skin: AuraPanelSkin,
    modifier: Modifier = Modifier,
) {
    val accent = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
    val plate = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
    val line = if (skin.enabled) skin.line else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AbsoluteSmoothCornerShape(20.dp, 60))
            .background(plate)
            .border(1.dp, line, AbsoluteSmoothCornerShape(20.dp, 60))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Medidor FFT en vivo",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    !snapshot.hasSignal -> "Sin señal"
                    snapshot.peak >= EQ_FFT_SATURATE_THRESHOLD -> "Saturación"
                    snapshot.peak >= EQ_FFT_AMBER_THRESHOLD -> "Cerca del límite"
                    else -> "OK"
                },
                style = MaterialTheme.typography.labelSmall,
                color = eqFftTrafficColor(snapshot.peak, accent),
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            val barGap = 3.dp.toPx()
            val barWidth = ((size.width - barGap * (EQ_FFT_BAR_COUNT - 1)) / EQ_FFT_BAR_COUNT).coerceAtLeast(2f)
            val h = size.height
            snapshot.bars.forEachIndexed { i, level ->
                val x = i * (barWidth + barGap)
                val barH = (level * h * 0.92f).coerceAtLeast(if (level > 0.02f) 2.dp.toPx() else 0f)
                val color = eqFftTrafficColor(level, accent)
                drawRoundRect(
                    color = line.copy(alpha = 0.35f),
                    topLeft = Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(barWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
                if (barH > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, h - barH),
                        size = androidx.compose.ui.geometry.Size(barWidth, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    )
                }
            }
            val satY = h * (1f - EQ_FFT_SATURATE_THRESHOLD * 0.92f)
            drawLine(
                accent.copy(alpha = 0.45f),
                Offset(0f, satY),
                Offset(size.width, satY),
                1.dp.toPx(),
            )
        }
    }
}

/** Slider throw at phone width. Grown by [eqVerticalScale] when the pane is wider. */
private val BAND_SLIDER_TRAVEL = 200.dp

/** Height of the read-only EQ curve preview at phone width. */
private val EQ_CURVE_HEIGHT = 130.dp

/** Height of the interactive parametric-EQ graph at phone width. */
private val PEQ_GRAPH_HEIGHT = 220.dp

/** Hard ceiling on [eqVerticalScale] — the graphs may get roomier, never "gigante" (#50). */
private const val EQ_VERTICAL_SCALE_MAX = 1.45f

/**
 * Width at which the graphs are considered "phone sized" and keep their original heights exactly. Anything
 * narrower than this scales by 1f, so the whole growth path is inert on a normal phone.
 */
private val EQ_CURVE_SCALE_BASE_WIDTH = 400.dp

/**
 * How much taller the EQ's fixed-height elements (band travel, curve preview, PEQ graph) should get for the
 * width actually available. 1f at or below [base] — so a phone is byte-for-byte unchanged — then grows in
 * proportion to the extra width and is clamped at [EQ_VERTICAL_SCALE_MAX].
 *
 * Deliberately sub-linear in effect: the owner's complaint (#50) is that an unfolded foldable makes everything
 * GIGANTIC, so this exists to stop the graphs looking like squashed strips next to now-wider content, not to
 * scale the UI up with the screen.
 */
private fun eqVerticalScale(available: Dp, base: Dp): Float =
    if (available <= base) 1f else (available / base).coerceAtMost(EQ_VERTICAL_SCALE_MAX)

/**
 * The existing EQ controls — enable card, preamp, curve preview + presets, mode toggle, the graphic /
 * parametric editor, the save / custom-preset / export / device rows. Extracted verbatim so both the
 * narrow (single column) and wide (left column) layouts render identical content. This is laid out
 * inside a vertically-scrolling [Column] supplied by the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.EqMainContent(
    skin: AuraPanelSkin,
    viewModel: AxionEqViewModel,
    enabled: Boolean,
    graphicEnabled: Boolean,
    bandGains: FloatArray,
    preamp: Float,
    autoEqActive: Boolean,
    isDirty: Boolean,
    customProfiles: List<SavedEQProfile>,
    eqMode: EqMode,
    peqBands: List<ParametricEQBand>,
    onSaveClick: () -> Unit,
    onManageClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onAutoEqClick: () -> Unit,
    onSliderDragActiveChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val highPerf by rememberPreference(HighPerformanceModeKey, false)
    val fftDefault = remember(highPerf, context) { eqFftMeterDefaultEnabled(context, highPerf) }
    val (fftMeterEnabled, onFftMeterChange) = rememberPreference(EqFftMeterEnabledKey, fftDefault)

    val playerConnection = LocalPlayerConnection.current
    var audioSessionId by remember {
        mutableIntStateOf(playerConnection?.player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET)
    }
    var meterPlaying by remember { mutableStateOf(false) }
    var visualizerGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(playerConnection) {
        val player = playerConnection?.player
        if (player == null) {
            audioSessionId = C.AUDIO_SESSION_ID_UNSET
            meterPlaying = false
            onDispose { }
        } else {
            val fullListener = object : Player.Listener {
                override fun onAudioSessionIdChanged(sessionId: Int) {
                    audioSessionId = sessionId
                    visualizerGeneration++
                }

                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    // Same session id across tracks — still force Visualizer reattach.
                    audioSessionId = player.audioSessionId
                    visualizerGeneration++
                    meterPlaying = player.isPlaying || player.playWhenReady
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    meterPlaying = playing || player.playWhenReady
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    meterPlaying = player.isPlaying || player.playWhenReady
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                        val sid = player.audioSessionId
                        if (sid != audioSessionId) {
                            audioSessionId = sid
                            visualizerGeneration++
                        }
                    }
                }
            }
            player.addListener(fullListener)
            audioSessionId = player.audioSessionId
            meterPlaying = player.isPlaying || player.playWhenReady
            onDispose { player.removeListener(fullListener) }
        }
    }
    // Video swap creates a new audio source and can silently kill the active Visualizer even when
    // the session id stays the same. Collect videoMode changes and force a rebind each time.
    LaunchedEffect(playerConnection) {
        playerConnection?.videoMode?.collect {
            visualizerGeneration++
        }
    }

    val fftSnapshot by rememberEqFftMeter(
        audioSessionId = audioSessionId,
        enabled = fftMeterEnabled,
        playing = meterPlaying,
        generation = visualizerGeneration,
        onForceRebind = { visualizerGeneration++ },
    )
    // Model output gain the same way the EQ engine does (preamp minus band-boost headroom).
    val expectedGainDb = remember(preamp, bandGains, enabled) {
        if (!enabled) {
            0.0
        } else {
            headroomPreampDb(
                userPreampDb = preamp.toDouble(),
                enabledBandGainsDb = bandGains.map { it.toDouble() },
            )
        }
    }
    val displayPeak = remember(fftSnapshot.peak, expectedGainDb) {
        (fftSnapshot.peak * 10.0.pow(expectedGainDb / 20.0).toFloat()).coerceIn(0f, 1f)
    }
    val displaySnapshot = remember(fftSnapshot, displayPeak, expectedGainDb) {
        val scale = if (fftSnapshot.peak > 1e-4f) {
            displayPeak / fftSnapshot.peak
        } else {
            10.0.pow(expectedGainDb / 20.0).toFloat()
        }
        EqFftSnapshot(
            bars = FloatArray(EQ_FFT_BAR_COUNT) { i ->
                (fftSnapshot.bars.getOrElse(i) { 0f } * scale).coerceIn(0f, 1f)
            },
            peak = displayPeak,
            hasSignal = fftSnapshot.hasSignal,
        )
    }

    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.equalizer),
                title = { Text(stringResource(R.string.eq_enable_title)) },
                description = { Text(stringResource(R.string.eq_enable_summary)) },
                trailingContent = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { viewModel.setEnabled(it) },
                        thumbContent = {
                            Icon(
                                painter = painterResource(id = if (enabled) R.drawable.check else R.drawable.close),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        },
                    )
                },
                onClick = { viewModel.setEnabled(!enabled) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.graphic_eq),
                title = { Text("Medidor FFT en vivo") },
                description = {
                    Text(
                        if (highPerf || DeviceCapabilities.tier(context) == DeviceTier.LOW) {
                            "Visualiza el espectro en tiempo real. Desactivado por defecto en Modo Rendimiento o gama baja."
                        } else {
                            "Visualiza el espectro en tiempo real con semáforo verde / ámbar / cian."
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = fftMeterEnabled,
                        onCheckedChange = onFftMeterChange,
                    )
                },
                onClick = { onFftMeterChange(!fftMeterEnabled) },
            ),
        ),
    )

    SpatialAudioSection(skin = skin)

    val (safeVolume, onSafeVolumeChange) = rememberPreference(SafeVolumeEnabledKey, defaultValue = true)
    val (tidalSimulation, onTidalSimulationChange) = rememberPreference(TidalSimulationEnabledKey, defaultValue = true)

    Material3SettingsGroup(
        title = "Mejoras de Audio y Dinámica",
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.volume_up),
                title = { Text("Volumen Seguro") },
                description = { Text("Previene saturación, normaliza volumen entre pistas y protege tu audición.") },
                trailingContent = {
                    Switch(
                        checked = safeVolume,
                        onCheckedChange = onSafeVolumeChange,
                    )
                },
                onClick = { onSafeVolumeChange(!safeVolume) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.tune),
                title = { Text("Firma de sonido Tidal") },
                description = { Text("Simulación de audio de alta fidelidad con calibración de armónicos cálidos y escenario sonoro expansivo.") },
                trailingContent = {
                    Switch(
                        checked = tidalSimulation,
                        onCheckedChange = onTidalSimulationChange,
                    )
                },
                onClick = { onTidalSimulationChange(!tidalSimulation) },
            ),
        ),
    )

    // Preamp applies to both modes (graphic + parametric) so it stays visible always.
    PreampCard(
        skin = skin,
        preamp = preamp,
        enabled = enabled,
        fftPeak = if (fftMeterEnabled) displayPeak else null,
        onPreampChange = { viewModel.setPreampLive(it) },
        onCommit = { viewModel.commit() },
        onDragActiveChange = onSliderDragActiveChange,
    )

    if (fftMeterEnabled) {
        EqFftMeter(snapshot = displaySnapshot, skin = skin)
    }

    // Curve preview + factory presets drive/show the 10-band (EqConstants.BAND_COUNT) GRAPHIC curve
    // only — hidden in PARAMETRIC mode where they'd be inaudible and misleading.
    if (eqMode == EqMode.GRAPHIC) {
        // Live preview of the overall EQ curve — easier to read the shape than 10 separate sliders.
        EqCurvePreview(bandGains = bandGains, enabled = enabled, skin = skin)

        FactoryPresetGrid(
            skin = skin,
            bandGains = bandGains,
            enabled = graphicEnabled,
            onPresetClick = { viewModel.applyPreset(it) },
        )
    }

    // Auto-EQ picker lives here (not under Sonido). Active = status chip + clear; inactive = entry button.
    if (autoEqActive) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { },
                enabled = false,
                label = { Text("Auto-EQ activo — tu EQ se suma encima") },
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(
                onClick = { viewModel.clearAutoEq() },
            ) {
                Text("Quitar Auto-EQ")
            }
        }
        OutlinedButton(
            onClick = onAutoEqClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Cambiar Auto-EQ (por auricular)") }
    } else {
        OutlinedButton(
            onClick = onAutoEqClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Auto-EQ (por auricular)") }
    }

    // Mode toggle: Gráfico (10-band, EqConstants.BAND_COUNT, default) vs Paramétrico (5–8 free PEQ bands). Stays enabled while
    // Auto-EQ is active (Auto-EQ is a separate cascaded stage, not a lock).
    EqModeToggle(
        eqMode = eqMode,
        enabled = graphicEnabled,
        onModeChange = { viewModel.setEqMode(it) },
    )

    when (eqMode) {
        EqMode.GRAPHIC -> BandEqCard(
            skin = skin,
            bandGains = bandGains,
            enabled = graphicEnabled,
            onBandChange = { i, v -> viewModel.setBandGainLive(i, v) },
            onBandCommit = { viewModel.commit() },
            onReset = { viewModel.reset() },
            onDragActiveChange = onSliderDragActiveChange,
        )
        EqMode.PARAMETRIC -> PeqGraphEditor(
            bands = peqBands,
            enabled = graphicEnabled,
            onBandChange = { i, freq, q, gain, type ->
                viewModel.setPeqBand(i, freq, q, gain, type)
            },
            onBandCommit = { viewModel.commitPeq() },
            onAddBand = { viewModel.addPeqBand() },
            onRemoveBand = { viewModel.removePeqBand(it) },
            onReset = { viewModel.resetPeq() },
        )
    }

    AnimatedVisibility(
        visible = isDirty && enabled,
        modifier = Modifier.align(Alignment.CenterHorizontally),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        OutlinedButton(
            onClick = onSaveClick,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.eq_save), style = MaterialTheme.typography.labelLarge)
        }
    }

    if (customProfiles.isNotEmpty()) {
        CustomPresetRow(
            customProfiles = customProfiles,
            bandGains = bandGains,
            enabled = enabled,
            onApplyProfile = { viewModel.applySavedProfile(it) },
            onEditClick = onManageClick,
        )
    }

    // Export / import EQ profiles (EQ curve + effects) as a JSON file.
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportProfiles(it) } }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importProfiles(it) } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { exportLauncher.launch("aura-eq-perfiles.json") },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Exportar perfiles") }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Importar") }
    }

    // Assign EQ profiles to output devices (phone / Bluetooth), applied automatically on connect.
    OutlinedButton(
        onClick = onDeviceClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) { Text("EQ por dispositivo") }

    Spacer(modifier = Modifier.height(60.dp))
}


@Composable
private fun EqCurvePreview(bandGains: FloatArray, enabled: Boolean, skin: AuraPanelSkin) {
    val accent = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
    val curveColor = if (enabled) accent else accent.copy(alpha = 0.35f)
    val gridColor = if (skin.enabled) skin.hairline else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val plate = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
    val line = if (skin.enabled) skin.line else Color.Transparent
    val maxGain = iad1tya.echo.music.eq.data.EqConstants.GAIN_MAX
    
    // Animate color based on enablement
    val animatedCurveColor by androidx.compose.animation.animateColorAsState(
        targetValue = curveColor,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
    )

    // #49(c) — the preview was a flat 130dp at every width, so on a wide pane it read as a squashed letterbox.
    // Height now follows the measured width via [eqVerticalScale] (clamped, and exactly 130dp on a phone).
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val curveHeight = EQ_CURVE_HEIGHT * eqVerticalScale(maxWidth, EQ_CURVE_SCALE_BASE_WIDTH)
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(curveHeight)
            .clip(racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(24.dp, 60))
            .background(plate)
            .then(if (skin.enabled) Modifier.border(1.dp, line, AbsoluteSmoothCornerShape(24.dp, 60)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        fun line(yFrac: Float, alpha: Float) = drawLine(
            gridColor.copy(alpha = alpha),
            androidx.compose.ui.geometry.Offset(0f, h * yFrac),
            androidx.compose.ui.geometry.Offset(w, h * yFrac),
            1.dp.toPx(),
        )
        // Background Grid
        line(0.5f, 0.4f)
        line(0.2f, 0.15f)
        line(0.8f, 0.15f)

        val n = bandGains.size
        if (n < 2) return@Canvas
        fun px(i: Int) = w * i / (n - 1).toFloat()
        fun py(g: Float) = (mid - (g / maxGain) * (mid * 0.85f)).coerceIn(0f, h)

        val curve = androidx.compose.ui.graphics.Path()
        val fill = androidx.compose.ui.graphics.Path()
        curve.moveTo(px(0), py(bandGains[0]))
        fill.moveTo(px(0), h)
        fill.lineTo(px(0), py(bandGains[0]))
        for (i in 1 until n) {
            val prevX = px(i - 1)
            val prevY = py(bandGains.getOrElse(i - 1) { 0f })
            val curX = px(i)
            val curY = py(bandGains.getOrElse(i) { 0f })
            val midX = (prevX + curX) / 2f
            curve.cubicTo(midX, prevY, midX, curY, curX, curY)
            fill.cubicTo(midX, prevY, midX, curY, curX, curY)
        }
        fill.lineTo(px(n - 1), h)
        fill.close()

        // Gradient Fill
        val fillBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(animatedCurveColor.copy(alpha = 0.35f), animatedCurveColor.copy(alpha = 0.0f)),
            startY = 0f,
            endY = h
        )
        drawPath(fill, fillBrush)

        // Neon Glow (3 passes of blur)
        if (enabled) {
            drawPath(
                curve,
                animatedCurveColor.copy(alpha = 0.15f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 16.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawPath(
                curve,
                animatedCurveColor.copy(alpha = 0.3f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        
        // Main Line
        drawPath(
            curve,
            animatedCurveColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqOverlayDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    if (premium) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AuraDialogWindowEffects(enabled = true)
            CompositionLocalProvider(LocalAuraFloatingChrome provides true) {
                AuraFloatingSurface(
                    modifier = modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(),
                    shape = AuraShapes.Card,
                ) {
                    Box(modifier = Modifier.padding(18.dp)) {
                        content()
                    }
                }
            }
        }
    } else {
        BasicAlertDialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 320.dp),
                shape = AbsoluteSmoothCornerShape(30.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Box(modifier = Modifier.padding(18.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DeviceEqDialog(
    customProfiles: List<iad1tya.echo.music.eq.data.SavedEQProfile>,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = iad1tya.echo.music.eq.data.EqDeviceProfileStore
    val outputs = remember { store.connectedOutputs(context) }
    var autoApply by remember {
        mutableStateOf(store.isAutoApplyEnabled(context))
    }
    val assignments = remember {
        androidx.compose.runtime.mutableStateMapOf<String, String?>().apply {
            outputs.forEach {
                put(it.key, store.assignedProfileId(context, it.key))
            }
        }
    }
    val skin = rememberAuraPanelSkin()
    val ink = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface
    val inkMuted = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
    EqOverlayDialog(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "EQ por dispositivo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ink,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Asigna un perfil de EQ a cada salida. Se aplicará solo cuando se conecte ese dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = inkMuted,
                )
                // Master feature switch — turning this OFF must NOT disable the equalizer itself.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Aplicar automáticamente",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ink,
                        )
                        Text(
                            "Si lo apagas, el EQ sigue igual; solo deja de cambiar al conectar un dispositivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = inkMuted,
                        )
                    }
                    Switch(
                        checked = autoApply,
                        onCheckedChange = {
                            autoApply = it
                            store.setAutoApplyEnabled(context, it)
                        },
                    )
                }
                if (customProfiles.isEmpty()) {
                    Text("Primero guarda al menos un perfil de EQ.", style = MaterialTheme.typography.bodyMedium, color = ink)
                }
                outputs.forEach { out ->
                    var expanded by remember { mutableStateOf(false) }
                    val selectedName = customProfiles.firstOrNull { it.id == assignments[out.key] }?.name ?: "Ninguno"
                    Column {
                        Text(
                            out.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ink,
                        )
                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                enabled = customProfiles.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                            ) { Text(selectedName) }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Ninguno") },
                                    onClick = {
                                        // Clear mapping only — never touches master EQ enable.
                                        assignments[out.key] = null
                                        store.assign(context, out.key, null)
                                        expanded = false
                                    },
                                )
                                customProfiles.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name) },
                                        onClick = {
                                            assignments[out.key] = p.id
                                            store.assign(context, out.key, p.id)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Listo") }
            }
        }
    }
}

@Composable
private fun PreampCard(
    skin: AuraPanelSkin,
    preamp: Float,
    enabled: Boolean,
    fftPeak: Float?,
    onPreampChange: (Float) -> Unit,
    onCommit: () -> Unit,
    onDragActiveChange: (Boolean) -> Unit = {},
) {
    val accent = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
    val cardColor = if (enabled) {
        if (skin.enabled) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow
    }
    val saturated = fftPeak != null && fftPeak >= EQ_FFT_SATURATE_THRESHOLD
    val nearSat = fftPeak != null && !saturated && fftPeak >= EQ_FFT_AMBER_THRESHOLD
    val badgeColor = when {
        saturated -> accent
        nearSat -> EqFftAmber
        else -> if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant
    }

    val animatedBg by androidx.compose.animation.animateColorAsState(targetValue = cardColor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(20.dp, 60))
            .background(animatedBg)
            .then(if (skin.enabled) Modifier.border(1.dp, skin.line, AbsoluteSmoothCornerShape(20.dp, 60)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Preamplificador",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) accent else if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (fftPeak != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(eqFftTrafficColor(fftPeak, accent)),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(badgeColor.copy(alpha = if (enabled) 0.15f else 0.05f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "%+.1f dB".format(preamp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                    )
                }
            }
        }
        Slider(
            value = preamp,
            onValueChange = {
                onDragActiveChange(true)
                onPreampChange(it)
            },
            onValueChangeFinished = {
                onDragActiveChange(false)
                onCommit()
            },
            valueRange = EqConstants.PREAMP_MIN..EqConstants.PREAMP_MAX,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.2f),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SpatialAudioSection(skin: AuraPanelSkin) {
    val engineStatus by CustomEqualizerAudioProcessor.engineStatus.collectAsState()
    val engineLive = engineStatus == SuperpoweredEngineStatus.HEALTHY ||
        engineStatus == SuperpoweredEngineStatus.UNKNOWN
    val (enabled, onEnabledChange) = rememberPreference(SpatialAudioEnabledKey, false)
    val (profile, onProfileChange) = rememberEnumPreference(SpatialAudioProfileKey, SpatialAudioProfile.WIDE_SURROUND)
    LaunchedEffect(profile) {
        if (profile !in SpatialAudioProfile.uiProfiles) {
            onProfileChange(SpatialAudioProfile.WIDE_SURROUND)
        }
    }
    val accent = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
    val fill = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow
    val line = if (skin.enabled) skin.line else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val ink = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface
    val inkMuted = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant

    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.spatial_tracking_apple),
                title = { Text(stringResource(R.string.spatial_audio_title)) },
                description = { Text(stringResource(R.string.spatial_audio_summary)) },
                trailingContent = {
                    Switch(
                        checked = enabled,
                        enabled = engineLive,
                        onCheckedChange = { onEnabledChange(it) },
                        thumbContent = {
                            Icon(
                                painter = painterResource(id = if (enabled) R.drawable.check else R.drawable.close),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        },
                    )
                },
                onClick = { if (engineLive) onEnabledChange(!enabled) },
            ),
        ),
    )

    if (!enabled) return

    Text(
        text = stringResource(R.string.spatial_audio_profiles_label),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
        color = accent,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        SpatialAudioProfile.uiProfiles.forEach { item ->
            val selected = profile == item
            val shape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .clip(shape)
                    .background(if (selected) accent.copy(alpha = 0.12f) else fill)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) accent else line,
                        shape = shape,
                    )
                    .clickable(enabled = engineLive) { onProfileChange(item) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(item.titleRes),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) accent else ink,
                    )
                    Text(
                        text = stringResource(item.summaryRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = inkMuted,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FactoryPresetGrid(
    skin: AuraPanelSkin,
    bandGains: FloatArray,
    enabled: Boolean,
    onPresetClick: (FactoryPreset) -> Unit,
) {
    val accent = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
    val fill = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow
    val line = if (skin.enabled) skin.line else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val ink = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface
    val inkMuted = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant

    // Derived from the CANONICAL enum order on purpose: several curves can match within the 0.5 dB
    // tolerance (FLAT matches anything near zero), so the winner must not depend on display order.
    val selectedPreset = FactoryPreset.entries.firstOrNull { preset ->
        bandGains.size == preset.gains.size &&
            bandGains.indices.all { kotlin.math.abs(bandGains[it] - preset.gains[it]) < 0.5f }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "PREAJUSTES AUDIÓFILOS",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
            color = accent,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3,
        ) {
            // Display order: short names first so FlowRow doesn't look jagged; last FULL row of 3
            // always holds the 3 longest names (by char count, desc-alpha tie-break) so wide chips
            // are grouped together instead of a long name appearing alone on the final row.
            // Selection still uses canonical enum order above (do not move the active chip to front).
            val displayPresets = remember {
                val sorted = FactoryPreset.entries.sortedWith(
                    compareBy({ it.displayName.length }, { it.displayName }),
                )
                val orphanCount = sorted.size % 3
                if (orphanCount == 0) {
                    sorted
                } else {
                    // Isolate the last (3 + orphanCount) items; re-sort them desc so the 3 longest
                    // occupy the last full row and the orphan(s) trail behind.
                    val pivotIdx = sorted.size - (3 + orphanCount)
                    val head = sorted.take(pivotIdx)
                    val tail = sorted.drop(pivotIdx).sortedWith(
                        compareByDescending<FactoryPreset> { it.displayName.length }
                            .thenByDescending { it.displayName },
                    )
                    head + tail
                }
            }
            displayPresets.forEach { preset ->
                key(preset) {
                    val isSelected = selectedPreset == preset
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .clip(shape)
                            .background(if (isSelected) accent.copy(alpha = 0.12f) else fill)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accent else line,
                                shape = shape,
                            )
                            .clickable(enabled = enabled) { onPresetClick(preset) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Text(
                                text = preset.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (enabled) ink else inkMuted,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectedPreset != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(AbsoluteSmoothCornerShape(12.dp, 60))
                    .background(if (skin.enabled) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    .then(if (skin.enabled) Modifier.border(1.dp, line, AbsoluteSmoothCornerShape(12.dp, 60)) else Modifier)
                    .padding(12.dp),
            ) {
                Text(
                    text = selectedPreset?.description ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun BandEqCard(
    skin: AuraPanelSkin,
    bandGains: FloatArray,
    enabled: Boolean,
    onBandChange: (Int, Float) -> Unit,
    onBandCommit: () -> Unit,
    onReset: () -> Unit,
    onDragActiveChange: (Boolean) -> Unit = {},
) {
    val plate = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow
    val line = if (skin.enabled) skin.line else Color.Transparent
    // True only after a VERTICAL fader move — used to freeze page scroll, not to lock band pan.
    var bandDragActive by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(plate)
                .then(if (skin.enabled) Modifier.border(1.dp, line, MaterialTheme.shapes.extraLarge) else Modifier)
                .padding(vertical = 16.dp),
        ) {
            // #49(b)/(owner request, 2026-08-18): the band row used to fall back to a `horizontalScroll`
            // Row (every band a hard 46dp Column) whenever the pane was narrower than the ~502dp all 10
            // bands need at that fixed width — i.e. on essentially every phone. That scroll container was
            // the actual cause of TWO complaints at once: not all bands were visible without swiping, AND
            // dragging a fader vertically also panned the row horizontally, because a real-world drag is
            // never perfectly vertical and `horizontalScroll` claims any horizontal delta in the gesture
            // regardless of who else wants it. Always giving each band an equal `weight(1f)` share — never
            // falling back to the scrolling narrow-column layout — fixes both: all BAND_COUNT sliders are
            // always on-screen (narrower on a phone, wider on a tablet), and with no horizontalScroll on
            // this Row there is nothing left to compete with a vertical fader drag.
            //
            // The count is ALWAYS EqConstants.BAND_COUNT (never a literal), so this follows the engine.
            val bandCount = EqConstants.BAND_COUNT
            val intrinsicRowWidth =
                (BAND_SLIDER_MIN_WIDTH * bandCount) +
                    (BAND_SLIDER_SPACING * (bandCount - 1)) +
                    (BAND_ROW_HORIZONTAL_PADDING * 2)
            // Taller travel on a big screen: 200dp of throw on a 900dp-wide pane reads as a squashed strip.
            // Scales off the SAME measured width, and is clamped so it can only ever grow modestly (#50 —
            // the complaint is that things get too big, so this never runs away).
            val travel = BAND_SLIDER_TRAVEL * eqVerticalScale(maxWidth, intrinsicRowWidth)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BAND_ROW_HORIZONTAL_PADDING),
                horizontalArrangement = Arrangement.spacedBy(BAND_SLIDER_SPACING),
            ) {
                for (band in 0 until bandCount) {
                    EqBandSlider(
                        label = EqConstants.FREQUENCY_LABELS[band],
                        value = bandGains.getOrElse(band) { 0f },
                        enabled = enabled,
                        accent = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                        onValueChange = { onBandChange(band, it) },
                        onValueChangeFinished = onBandCommit,
                        travel = travel,
                        onDragActiveChange = { active ->
                            bandDragActive = active
                            onDragActiveChange(active)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = onReset, enabled = enabled) {
                Icon(Icons.Rounded.Replay, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.eq_reset))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqModeToggle(
    eqMode: EqMode,
    enabled: Boolean,
    onModeChange: (EqMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = eqMode == EqMode.GRAPHIC,
            onClick = { if (enabled) onModeChange(EqMode.GRAPHIC) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text("Gráfico") }
        SegmentedButton(
            selected = eqMode == EqMode.PARAMETRIC,
            onClick = { if (enabled) onModeChange(EqMode.PARAMETRIC) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text("Paramétrico") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Interactive, drag-to-shape parametric EQ editor.
//
// The user shapes the sound by dragging dots on a live frequency-response curve (à la Wavelet /
// Poweramp / Neutron) instead of typing numbers. Horizontal drag = frequency (log), vertical = gain.
// The combined response is the per-band RBJ biquad magnitude summed in dB — the SAME formulas as
// [iad1tya.echo.music.eq.audio.BiquadFilter] so the drawn curve matches what you'll hear. The exact
// Hz / Q / dB stay visible (and editable via the Q slider + type selector) for purists.
// ─────────────────────────────────────────────────────────────────────────────────────────────────

// Reference sample rate for the on-screen magnitude curve. The real chain runs at the device rate,
// but for frequencies well below Nyquist the curve shape is rate-independent; 44.1 kHz is the natural
// reference and matches BiquadFilter's omega = 2π·f / sampleRate.
private const val PEQ_GRAPH_SAMPLE_RATE = 44100.0
private const val PEQ_FREQ_MIN = 20.0
private const val PEQ_FREQ_MAX = 20000.0
private const val PEQ_GAIN_RANGE = 18.0 // ±18 dB → full canvas height.

// ── Log-frequency X mapping (20 Hz … 20 kHz) ──────────────────────────────────────────────────────
private val LOG_F_MIN = ln(PEQ_FREQ_MIN)
private val LOG_F_SPAN = ln(PEQ_FREQ_MAX) - ln(PEQ_FREQ_MIN)

private fun freqToX(freq: Double, width: Float): Float {
    val t = ((ln(freq.coerceIn(PEQ_FREQ_MIN, PEQ_FREQ_MAX)) - LOG_F_MIN) / LOG_F_SPAN).toFloat()
    return t * width
}

private fun xToFreq(x: Float, width: Float): Double {
    val t = if (width <= 0f) 0.0 else (x / width).toDouble().coerceIn(0.0, 1.0)
    return kotlin.math.exp(LOG_F_MIN + t * LOG_F_SPAN)
}

// ── Linear gain Y mapping (+18 dB at top, 0 dB centered, −18 dB at bottom) ────────────────────────
private fun gainToY(gain: Double, height: Float): Float {
    val t = ((PEQ_GAIN_RANGE - gain.coerceIn(-PEQ_GAIN_RANGE, PEQ_GAIN_RANGE)) / (2.0 * PEQ_GAIN_RANGE)).toFloat()
    return t * height
}

private fun yToGain(y: Float, height: Float): Double {
    val t = if (height <= 0f) 0.5 else (y / height).toDouble().coerceIn(0.0, 1.0)
    return PEQ_GAIN_RANGE - t * (2.0 * PEQ_GAIN_RANGE)
}

/**
 * Magnitude (dB) of a single RBJ biquad band at frequency [f], evaluating |H(e^jω)| from the same
 * peaking / low-shelf / high-shelf coefficients [BiquadFilter] computes. We build the (b0,b1,b2 / a1,a2)
 * set (a0 normalized to 1) then evaluate H(z) = B(z)/A(z) at z = e^jω, ω = 2π·f / Fs.
 *
 * LSC/HSC use shelfSlope S = 1.0 to match BiquadFilter's default. A near-flat band returns ~0 dB.
 */
private fun bandMagnitudeDb(band: ParametricEQBand, f: Double): Double {
    if (!band.enabled) return 0.0
    val gain = band.gain
    val q = band.q.coerceAtLeast(1e-4)
    val f0 = band.frequency
    val omega0 = 2.0 * Math.PI * f0 / PEQ_GRAPH_SAMPLE_RATE
    val sinW0 = sin(omega0)
    val cosW0 = cos(omega0)

    // Coefficients (un-normalized b*/a*; a0 divided out at the end).
    val b0: Double; val b1: Double; val b2: Double
    val a0: Double; val a1: Double; val a2: Double
    when (band.filterType) {
        FilterType.LSC -> {
            val A = sqrt(10.0.pow(gain / 20.0))
            val s = 1.0
            val alpha = sinW0 / 2.0 * sqrt((A + 1.0 / A) * (1.0 / s - 1.0) + 2.0)
            val sqrtA = sqrt(A)
            val aPlus = A + 1.0
            val aMinus = A - 1.0
            val twoSqrtAAlpha = 2.0 * sqrtA * alpha
            b0 = A * (aPlus - aMinus * cosW0 + twoSqrtAAlpha)
            b1 = 2.0 * A * (aMinus - aPlus * cosW0)
            b2 = A * (aPlus - aMinus * cosW0 - twoSqrtAAlpha)
            a0 = aPlus + aMinus * cosW0 + twoSqrtAAlpha
            a1 = -2.0 * (aMinus + aPlus * cosW0)
            a2 = aPlus + aMinus * cosW0 - twoSqrtAAlpha
        }
        FilterType.HSC -> {
            val A = sqrt(10.0.pow(gain / 20.0))
            val s = 1.0
            val alpha = sinW0 / 2.0 * sqrt((A + 1.0 / A) * (1.0 / s - 1.0) + 2.0)
            val sqrtA = sqrt(A)
            val aPlus = A + 1.0
            val aMinus = A - 1.0
            val twoSqrtAAlpha = 2.0 * sqrtA * alpha
            b0 = A * (aPlus + aMinus * cosW0 + twoSqrtAAlpha)
            b1 = -2.0 * A * (aMinus + aPlus * cosW0)
            b2 = A * (aPlus + aMinus * cosW0 - twoSqrtAAlpha)
            a0 = aPlus - aMinus * cosW0 + twoSqrtAAlpha
            a1 = 2.0 * (aMinus - aPlus * cosW0)
            a2 = aPlus - aMinus * cosW0 - twoSqrtAAlpha
        }
        FilterType.LPQ -> { // RBJ low-pass (gain ignored) — matches BiquadFilter.calculateLowPassCoefficients.
            val alpha = sinW0 / (2.0 * q)
            b0 = (1.0 - cosW0) / 2.0
            b1 = 1.0 - cosW0
            b2 = (1.0 - cosW0) / 2.0
            a0 = 1.0 + alpha
            a1 = -2.0 * cosW0
            a2 = 1.0 - alpha
        }
        FilterType.HPQ -> { // RBJ high-pass (gain ignored) — matches BiquadFilter.calculateHighPassCoefficients.
            val alpha = sinW0 / (2.0 * q)
            b0 = (1.0 + cosW0) / 2.0
            b1 = -(1.0 + cosW0)
            b2 = (1.0 + cosW0) / 2.0
            a0 = 1.0 + alpha
            a1 = -2.0 * cosW0
            a2 = 1.0 - alpha
        }
        else -> { // FilterType.PK (peaking) — also the fallback for any non-PEQ type.
            val A = 10.0.pow(gain / 40.0)
            val alpha = sinW0 / (2.0 * q)
            b0 = 1.0 + alpha * A
            b1 = -2.0 * cosW0
            b2 = 1.0 - alpha * A
            a0 = 1.0 + alpha / A
            a1 = -2.0 * cosW0
            a2 = 1.0 - alpha / A
        }
    }

    // Normalize a0 → 1, exactly as BiquadFilter does.
    val nb0 = b0 / a0; val nb1 = b1 / a0; val nb2 = b2 / a0
    val na1 = a1 / a0; val na2 = a2 / a0

    // Evaluate |H(e^jω)| at the display frequency. z^-1 = e^-jω, z^-2 = e^-2jω.
    val omega = 2.0 * Math.PI * f / PEQ_GRAPH_SAMPLE_RATE
    val cw = cos(omega); val sw = sin(omega)
    val c2w = cos(2.0 * omega); val s2w = sin(2.0 * omega)
    // Numerator B = b0 + b1·e^-jω + b2·e^-2jω
    val numRe = nb0 + nb1 * cw + nb2 * c2w
    val numIm = -(nb1 * sw + nb2 * s2w)
    // Denominator A = 1 + a1·e^-jω + a2·e^-2jω
    val denRe = 1.0 + na1 * cw + na2 * c2w
    val denIm = -(na1 * sw + na2 * s2w)
    val numMag = hypot(numRe, numIm)
    val denMag = hypot(denRe, denIm).coerceAtLeast(1e-12)
    val mag = numMag / denMag
    return 20.0 * log10(mag.coerceAtLeast(1e-9))
}

/** Combined response (dB) = sum of every band's magnitude in dB at frequency [f]. */
private fun combinedMagnitudeDb(bands: List<ParametricEQBand>, f: Double): Double {
    var sum = 0.0
    for (b in bands) sum += bandMagnitudeDb(b, f)
    return sum
}

private val PEQ_NODE_COLORS = listOf(
    0xFF4FC3F7, 0xFFFF8A65, 0xFFBA68C8, 0xFF81C784,
    0xFFFFD54F, 0xFF4DD0E1, 0xFFF06292, 0xFF9575CD,
)

/**
 * Interactive drag-to-shape parametric EQ editor. The user drags filled circle nodes on a live
 * frequency-response graph to shape the sound; the exact Hz / Q / dB stay visible and a detail panel
 * exposes Q + filter type + remove for the selected band.
 *
 * @param onBandChange live setter — (index, freq?, q?, gain?, type?); the VM clamps every range.
 * @param onBandCommit persists on drag/slider settle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeqGraphEditor(
    bands: List<ParametricEQBand>,
    enabled: Boolean,
    onBandChange: (index: Int, freq: Double?, q: Double?, gain: Double?, type: FilterType?) -> Unit,
    onBandCommit: () -> Unit,
    onAddBand: () -> Unit,
    onRemoveBand: (index: Int) -> Unit,
    onReset: () -> Unit,
) {
    // Selection survives add/remove: it's always coerced into the current 0..lastIndex below.
    var selectedIndex by remember { mutableStateOf(0) }
    // Remember the previous band count so we can auto-select the NEW (last) band right after an add.
    val prevCount = remember { mutableStateOf(bands.size) }
    LaunchedEffect(bands.size) {
        if (bands.size > prevCount.value) selectedIndex = bands.lastIndex // band added → select it
        prevCount.value = bands.size
    }
    // Keep selection valid for every render (removal, profile load, empty list).
    selectedIndex = if (bands.isEmpty()) 0 else selectedIndex.coerceIn(0, bands.lastIndex)
    val selected = bands.getOrNull(selectedIndex)

    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val curveColor = if (enabled) primary else primary.copy(alpha = 0.35f)
    val fillColor = curveColor.copy(alpha = 0.14f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroLineColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Arrastra los puntos para dar forma al sonido",
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
        )

        // ── The interactive frequency-response graph ──────────────────────────────────────────────
        // #49(c) — height follows the measured width (clamped by [eqVerticalScale]); on a wide pane the extra
        // vertical room also makes the draggable nodes easier to grab. Exactly 220dp at phone width.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val peqGraphHeight = PEQ_GRAPH_HEIGHT * eqVerticalScale(maxWidth, EQ_CURVE_SCALE_BASE_WIDTH)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(peqGraphHeight)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .then(if (enabled) Modifier else Modifier.graphicsLayer { alpha = 0.4f }),
        ) {
            // dp→px once, used by both the renderer and the hit-test/drag math.
            val touchRadiusPx = with(density) { 28.dp.toPx() }
            val nodeRadiusPx = with(density) { 9.dp.toPx() }
            val selectedRadiusPx = with(density) { 13.dp.toPx() }
            // Always read the LIVE band positions in the gesture (without relaunching pointerInput), so hit-testing
            // a node is correct even right after another node was moved.
            val latestBands = rememberUpdatedState(bands)

            // ONE unified gesture: select the grabbed node on touch-DOWN (immediate, NO touch-slop) and drag it
            // until the finger lifts. A single pointerInput keyed on Unit (never relaunches mid-interaction) avoids
            // the tap-vs-drag detector conflict that made nodes hard to grab and made switching to another node
            // fail. drag() applies every move with no slop, so dragging is immediate and precise.
            val gestureModifier = if (enabled) {
                Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val idx = nearestNode(latestBands.value, down.position, w, h, touchRadiusPx)
                        if (idx < 0) return@awaitEachGesture
                        selectedIndex = idx
                        down.consume()
                        var moved = false
                        drag(down.id) { change ->
                            moved = true
                            val p = change.position
                            val freq = xToFreq(p.x.coerceIn(0f, w), w)
                            val gain = yToGain(p.y.coerceIn(0f, h), h)
                            onBandChange(idx, freq, null, gain, null)
                            change.consume()
                        }
                        if (moved) onBandCommit()
                    }
                }
            } else {
                Modifier
            }

            Canvas(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
                val w = size.width
                val h = size.height
                val strokePx = 2.5.dp.toPx()

                // Horizontal gain gridlines: 0 dB emphasized, faint at ±6 / ±12.
                drawLine(zeroLineColor.copy(alpha = 0.55f), Offset(0f, gainToY(0.0, h)), Offset(w, gainToY(0.0, h)), 1.5.dp.toPx())
                for (g in listOf(6.0, 12.0)) {
                    val a = 0.22f
                    drawLine(gridColor.copy(alpha = a), Offset(0f, gainToY(g, h)), Offset(w, gainToY(g, h)), 1.dp.toPx())
                    drawLine(gridColor.copy(alpha = a), Offset(0f, gainToY(-g, h)), Offset(w, gainToY(-g, h)), 1.dp.toPx())
                }
                // Vertical frequency gridlines + labels at 100 / 1k / 10k.
                val freqGuides = listOf(100.0 to "100", 1000.0 to "1k", 10000.0 to "10k")
                for ((fg, lbl) in freqGuides) {
                    val x = freqToX(fg, w)
                    drawLine(gridColor.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, h), 1.dp.toPx())
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = labelColor.copy(alpha = 0.8f).toArgb()
                            textSize = 9.dp.toPx()
                            isAntiAlias = true
                        }
                        drawText(lbl, x + 4.dp.toPx(), h - 4.dp.toPx(), paint)
                    }
                }

                // Combined response curve (one sample per pixel column) + a soft fill under it.
                if (bands.isNotEmpty() && w >= 1f) {
                    val steps = w.toInt().coerceAtLeast(2)
                    val curve = Path()
                    val fill = Path()
                    val mid = gainToY(0.0, h)
                    var first = true
                    for (i in 0..steps) {
                        val x = w * i / steps
                        val f = xToFreq(x, w)
                        val db = combinedMagnitudeDb(bands, f)
                        val y = gainToY(db, h).coerceIn(0f, h)
                        if (first) {
                            curve.moveTo(x, y)
                            fill.moveTo(x, mid)
                            fill.lineTo(x, y)
                            first = false
                        } else {
                            curve.lineTo(x, y)
                            fill.lineTo(x, y)
                        }
                    }
                    fill.lineTo(w, mid)
                    fill.close()
                    drawPath(fill, fillColor)
                    drawPath(curve, curveColor, style = Stroke(width = strokePx))
                }

                // Draggable band nodes. Selected node is larger + ringed.
                bands.forEachIndexed { index, band ->
                    val nodeColor = androidx.compose.ui.graphics.Color(PEQ_NODE_COLORS[index % PEQ_NODE_COLORS.size])
                    val cx = freqToX(band.frequency, w)
                    val cy = gainToY(band.gain, h)
                    val isSel = index == selectedIndex
                    val r = if (isSel) selectedRadiusPx else nodeRadiusPx
                    if (isSel) {
                        // Outer halo ring around the active node.
                        drawCircle(nodeColor.copy(alpha = 0.30f), radius = r + 7.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(onSurface, radius = r + 2.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
                    }
                    drawCircle(nodeColor, radius = r, center = Offset(cx, cy))
                    drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), radius = r * 0.32f, center = Offset(cx, cy))
                }
            }
        }
        }

        // ── Selected-band detail panel (Q slider + type + exact values + remove) ──────────────────
        if (selected != null) {
            val nodeColor = androidx.compose.ui.graphics.Color(PEQ_NODE_COLORS[selectedIndex % PEQ_NODE_COLORS.size])
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(nodeColor),
                        )
                        Text(
                            text = "Banda ${selectedIndex + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Exact numbers for the purist — live.
                    Text(
                        text = "${formatHz(selected.frequency)} · Q ${"%.1f".format(selected.q)} · ${"%+.1f".format(selected.gain)} dB",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                }

                // Ancho (Q) — the easiest way to set bandwidth.
                Column {
                    Text(
                        text = "Ancho (Q)",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                    Slider(
                        value = selected.q.toFloat(),
                        onValueChange = { onBandChange(selectedIndex, null, it.toDouble(), null, null) },
                        onValueChangeFinished = onBandCommit,
                        valueRange = PeqConstants.Q_MIN.toFloat()..PeqConstants.Q_MAX.toFloat(),
                        enabled = enabled,
                    )
                }

                // PK / LSC / HSC type selector.
                PeqTypeSelector(
                    selected = selected.filterType,
                    enabled = enabled,
                    onTypeChange = {
                        onBandChange(selectedIndex, null, null, null, it)
                        onBandCommit()
                    },
                )

                // Remove the selected band (floored at MIN_BANDS by the VM; disabled here too).
                val canRemove = enabled && bands.size > PeqConstants.MIN_BANDS
                TextButton(
                    onClick = { if (canRemove) onRemoveBand(selectedIndex) },
                    enabled = canRemove,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = if (canRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Quitar banda", color = if (canRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                }
            }
        }

        // ── Add a band  |  reset the whole PEQ to flat defaults ──────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onAddBand,
                enabled = enabled && bands.size < PeqConstants.MAX_BANDS,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Añadir banda")
            }
            OutlinedButton(
                onClick = onReset,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restablecer")
            }
        }
    }
}

/**
 * Hit-test: index of the nearest band node to [pos] within [touchRadiusPx], or −1 if none is close.
 * Distance is measured in canvas pixels using the same freq/gain → x/y mapping the renderer uses.
 */
private fun nearestNode(
    bands: List<ParametricEQBand>,
    pos: Offset,
    width: Float,
    height: Float,
    touchRadiusPx: Float,
): Int {
    var best = -1
    var bestDist = touchRadiusPx
    bands.forEachIndexed { index, band ->
        val cx = freqToX(band.frequency, width)
        val cy = gainToY(band.gain, height)
        val d = hypot(pos.x - cx, pos.y - cy)
        if (d <= bestDist) {
            bestDist = d
            best = index
        }
    }
    return best
}

/** Human-readable Hz: "120 Hz" below 1 kHz, "1.2 kHz" above. */
private fun formatHz(freq: Double): String =
    if (freq >= 1000.0) {
        val k = freq / 1000.0
        if (k >= 10.0) "${k.roundToInt()} kHz" else "%.1f kHz".format(k)
    } else {
        "${freq.roundToInt()} Hz"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeqTypeSelector(
    selected: FilterType,
    enabled: Boolean,
    onTypeChange: (FilterType) -> Unit,
) {
    val types = listOf(FilterType.PK, FilterType.LSC, FilterType.HSC)
    val labels = mapOf(FilterType.PK to "PK", FilterType.LSC to "LSC", FilterType.HSC to "HSC")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { i, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { if (enabled) onTypeChange(type) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = i, count = types.size),
            ) { Text(labels[type] ?: type.name) }
        }
    }
}

@Composable
private fun EqBandSlider(
    label: String,
    value: Float,
    enabled: Boolean,
    accent: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier.width(BAND_SLIDER_MIN_WIDTH),
    travel: Dp = BAND_SLIDER_TRAVEL,
    onDragActiveChange: (Boolean) -> Unit = {},
) {
    var fingerDown by remember { mutableStateOf(false) }
    val latestDrag = rememberUpdatedState(onDragActiveChange)
    val latestValue = rememberUpdatedState(value)
    val latestOnChange = rememberUpdatedState(onValueChange)
    val latestOnFinished = rememberUpdatedState(onValueChangeFinished)
    val travelPx = with(LocalDensity.current) { travel.toPx() }
    // Rotated slider: finger moves vertically on screen → parent verticalScroll steals the gesture
    // unless we consume nested scroll while the fader is actually moving.
    val blockParentScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (fingerDown) Offset(0f, available.y) else Offset.Zero
            }
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "%+d".format(value.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) accent else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // The vertical slider is a HORIZONTAL Slider rotated -90°, so the Box height and the Slider width are
        // the SAME dimension (the travel) and must stay in lockstep — hence one `travel` value driving both.
        // Fixed box + clip: rotated Slider must not expand the parent while dragging
        // (owner: "el diseño se mueve conmigo").
        Box(
            modifier = Modifier
                .height(travel)
                // fillMaxWidth, not a fixed BAND_SLIDER_MIN_WIDTH: the parent Column now gets an equal
                // `weight(1f)` share of the row (all BAND_COUNT bands always on-screen — see the row's own
                // comment), so the actual draggable control must follow that allotted width instead of a
                // constant 46dp, or every band beyond a handful would overlap/clip on a phone-width row.
                .fillMaxWidth()
                .clip(RectangleShape)
                .nestedScroll(blockParentScroll),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = value,
                onValueChange = {
                    latestDrag.value(true)
                    onValueChange(it)
                },
                onValueChangeFinished = {
                    latestDrag.value(false)
                    onValueChangeFinished()
                },
                valueRange = EqConstants.GAIN_MIN..EqConstants.GAIN_MAX,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = accent.copy(alpha = 0.2f),
                ),
                modifier = Modifier
                    .width(travel)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                            ),
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                -placeable.width / 2 + placeable.height / 2,
                                placeable.width / 2 - placeable.height / 2,
                            )
                        }
                    }
                    .graphicsLayer { rotationZ = -90f },
            )
            // Overlay takes pointers. Vertical slop → fader. Horizontal slop is left unconsumed so
            // the band row can pan (pressed swipe left/right). Do NOT lock on pointer-down.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(enabled, travelPx) {
                        if (!enabled) return@pointerInput
                        detectVerticalDragGestures(
                            onDragStart = {
                                fingerDown = true
                                latestDrag.value(true)
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val range = EqConstants.GAIN_MAX - EqConstants.GAIN_MIN
                                val next = (latestValue.value - dragAmount / travelPx * range)
                                    .coerceIn(EqConstants.GAIN_MIN, EqConstants.GAIN_MAX)
                                latestOnChange.value(next)
                            },
                            onDragEnd = {
                                fingerDown = false
                                latestDrag.value(false)
                                latestOnFinished.value()
                            },
                            onDragCancel = {
                                fingerDown = false
                                latestDrag.value(false)
                            },
                        )
                    },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPresetRow(
    customProfiles: List<SavedEQProfile>,
    bandGains: FloatArray,
    enabled: Boolean,
    onApplyProfile: (SavedEQProfile) -> Unit,
    onEditClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.eq_label_custom),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
            if (enabled) {
                androidx.compose.material3.IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            customProfiles.forEach { profile ->
                val gains = FloatArray(EqConstants.BAND_COUNT) { i ->
                    profile.bands.getOrNull(i)?.gain?.toFloat() ?: 0f
                }
                val selected = bandGains.indices.all { abs(bandGains[it] - gains[it]) < 0.5f }
                FilterChip(
                    selected = selected,
                    onClick = { if (enabled) onApplyProfile(profile) },
                    enabled = enabled,
                    label = { Text(profile.name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    val skin = rememberAuraPanelSkin()
    val ink = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface
    val accent = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    EqOverlayDialog(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(blockShape)
                    .then(
                        if (skin.enabled && skin.darkGround) Modifier.background(Color.Transparent)
                        else Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.eq_save_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.eq_save_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = if (skin.enabled) skin.line else MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss, shape = actionShape) {
                    Text(text = stringResource(R.string.cancel))
                }
                OutlinedButton(
                    onClick = { if (name.isNotBlank()) onSave(name) },
                    enabled = name.isNotBlank(),
                    shape = actionShape,
                ) {
                    Text(text = stringResource(R.string.eq_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagePresetsDialog(
    customProfiles: List<SavedEQProfile>,
    onDismiss: () -> Unit,
    onDeleteSelected: (List<String>) -> Unit,
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    val skin = rememberAuraPanelSkin()
    val ink = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface
    val inkMuted = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val hairline = if (skin.enabled) skin.hairline else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    EqOverlayDialog(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(blockShape)
                    .then(
                        if (skin.enabled && skin.darkGround) Modifier.background(Color.Transparent)
                        else Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.eq_manage_presets),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = hairline,
                )
                if (customProfiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.eq_no_custom_presets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkMuted,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(customProfiles) { profile ->
                            val isSelected = selectedIds.contains(profile.id)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        if (isSelected) selectedIds.remove(profile.id)
                                        else selectedIds.add(profile.id)
                                    }
                                    .padding(vertical = 4.dp),
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it == true) selectedIds.add(profile.id)
                                        else selectedIds.remove(profile.id)
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ink,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(bottom = 4.dp),
                color = hairline,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss, shape = actionShape) {
                    Text(text = stringResource(R.string.cancel))
                }
                if (selectedIds.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { onDeleteSelected(selectedIds.toList()) },
                        shape = actionShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(text = stringResource(R.string.eq_delete_selected))
                    }
                }
            }
        }
    }
}
