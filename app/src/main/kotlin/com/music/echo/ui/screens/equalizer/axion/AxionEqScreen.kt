package iad1tya.echo.music.ui.screens.equalizer.axion

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import iad1tya.echo.music.R
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.EqMode
import iad1tya.echo.music.eq.data.FactoryPreset
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import iad1tya.echo.music.eq.data.SavedEQProfile
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AxionEqScreen(
    onBackClick: () -> Unit,
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

    // The graphic EQ is locked while an Auto-EQ headphone profile is active (both write the same
    // band array). Any manual edit / preset clears autoEqActive in the ViewModel.
    val graphicEnabled = enabled && !autoEqActive

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.echo_equalizer)) },
                navigationIcon = {
                    iad1tya.echo.music.ui.component.IconButton(onClick = onBackClick, onLongClick = {}) {
                        Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        // Foldable / large-screen aware: on a WIDE display (e.g. an unfolded Galaxy Z Fold inner
        // screen) we show the EQ controls and the DSP effect switches side-by-side in two columns so
        // both are visible without scrolling between them. On a NARROW display (folded / phone) we keep
        // the original single scrolling column unchanged.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Material 'expanded' breakpoint — matches the Z Fold inner display.
            val wide = maxWidth >= 840.dp

            if (wide) {
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    // LEFT: the existing EQ controls, scrolling independently.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        EqMainContent(
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
                        )
                    }
                    // RIGHT: the DSP effect switches (shared DataStore keys with SoundSettings),
                    // scrolling independently.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DspEffectsColumn()
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            } else {
                // NARROW (folded / phone): unchanged single scrolling column.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EqMainContent(
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
                    )
                }
            }
        }
    }
}

/**
 * The existing EQ controls — enable card, preamp, curve preview + presets, mode toggle, the graphic /
 * parametric editor, the save / custom-preset / export / device rows. Extracted verbatim so both the
 * narrow (single column) and wide (left column) layouts render identical content. This is laid out
 * inside a vertically-scrolling [Column] supplied by the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.EqMainContent(
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
) {
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
        ),
    )

    // Preamp applies to both modes (graphic + parametric) so it stays visible always.
    PreampCard(preamp = preamp, enabled = enabled, onPreampChange = { viewModel.setPreampLive(it) }, onCommit = { viewModel.commit() })

    // Curve preview + factory presets drive/show the 24-band GRAPHIC curve only — hidden in
    // PARAMETRIC mode where they'd be inaudible and misleading.
    if (eqMode == EqMode.GRAPHIC) {
        // Live preview of the overall EQ curve — easier to read the shape than 24 separate sliders.
        EqCurvePreview(bandGains = bandGains, enabled = enabled)

        FactoryPresetRow(
            bandGains = bandGains,
            enabled = graphicEnabled,
            onPresetClick = { viewModel.applyPreset(it) },
        )
    }

    if (autoEqActive) {
        Text(
            text = "Auto-EQ activo — el ecualizador gráfico está bloqueado para preservar la corrección de tus auriculares.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        androidx.compose.material3.TextButton(
            onClick = { viewModel.unlockGraphic() },
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Text("Cambiar a manual")
        }
    }

    // Mode toggle: Gráfico (24-band, default) vs Paramétrico (5–8 free PEQ bands). Disabled
    // while Auto-EQ is locked so the user can't switch curves under the lock.
    EqModeToggle(
        eqMode = eqMode,
        enabled = graphicEnabled,
        onModeChange = { viewModel.setEqMode(it) },
    )

    when (eqMode) {
        EqMode.GRAPHIC -> BandEqCard(
            bandGains = bandGains,
            enabled = graphicEnabled,
            onBandChange = { i, v -> viewModel.setBandGainLive(i, v) },
            onBandCommit = { viewModel.commit() },
            onReset = { viewModel.reset() },
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

/**
 * DSP effect switches surfaced into the EQ screen's wide (two-column) layout. These are the SAME
 * switches that live in SoundSettings — backed by the SAME DataStore keys via [rememberPreference] —
 * so toggling here reflects in SoundSettings and vice-versa. No new keys are invented and no ViewModel
 * is touched; this is purely an additive UI surface. Laid out inside a vertically-scrolling [Column]
 * supplied by the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.DspEffectsColumn() {
    val (auraSignature, onAuraSignatureChange) =
        rememberPreference(iad1tya.echo.music.constants.AuraSignatureToneEnabledKey, defaultValue = true)
    val (audioNormalization, onAudioNormalizationChange) =
        rememberPreference(iad1tya.echo.music.constants.AudioNormalizationKey, defaultValue = true)
    val (jrLoudness, onJrLoudnessChange) =
        rememberPreference(iad1tya.echo.music.constants.JrLoudnessEnabledKey, defaultValue = false)
    val (jrExciter, onJrExciterChange) =
        rememberPreference(iad1tya.echo.music.constants.JrExciterEnabledKey, defaultValue = false)
    val (jrExciterAmt, onJrExciterAmtChange) =
        rememberPreference(iad1tya.echo.music.constants.JrExciterAmountKey, defaultValue = 0.15f)
    val (jrStereo, onJrStereoChange) =
        rememberPreference(iad1tya.echo.music.constants.JrStereoWidthEnabledKey, defaultValue = false)
    val (jrStereoAmt, onJrStereoAmtChange) =
        rememberPreference(iad1tya.echo.music.constants.JrStereoWidthKey, defaultValue = 1.0f)
    val (jrDialogue, onJrDialogueChange) =
        rememberPreference(iad1tya.echo.music.constants.JrDialogueEnabledKey, defaultValue = false)
    val (jrDialogueAmt, onJrDialogueAmtChange) =
        rememberPreference(iad1tya.echo.music.constants.JrDialogueAmountKey, defaultValue = 0.35f)

    fun thumb(checked: Boolean): @Composable () -> Unit = {
        Icon(
            painter = painterResource(id = if (checked) R.drawable.check else R.drawable.close),
            contentDescription = null,
            modifier = Modifier.size(SwitchDefaults.IconSize),
        )
    }

    Material3SettingsGroup(
        title = "Efectos de sonido",
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.graphic_eq),
                title = { Text("Firma Aura (cuerpo + aire)") },
                description = { Text("Curva suave: un poco más de graves y de agudos para más cuerpo y sensación de calidad. Sin distorsionar.") },
                trailingContent = {
                    Switch(checked = auraSignature, onCheckedChange = onAuraSignatureChange, thumbContent = thumb(auraSignature))
                },
                onClick = { onAuraSignatureChange(!auraSignature) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.volume_up),
                title = { Text(stringResource(R.string.audio_normalization)) },
                description = { Text("Lleva todas las canciones al mismo volumen (−14 LUFS) con limitador true-peak; sube las flojas sin distorsionar.") },
                trailingContent = {
                    Switch(checked = audioNormalization, onCheckedChange = onAudioNormalizationChange, thumbContent = thumb(audioNormalization))
                },
                onClick = { onAudioNormalizationChange(!audioNormalization) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.graphic_eq),
                title = { Text("Sonoridad") },
                description = { Text("Realce de graves y agudos Fletcher-Munson para escuchar a bajo volumen") },
                trailingContent = {
                    Switch(checked = jrLoudness, onCheckedChange = onJrLoudnessChange, thumbContent = thumb(jrLoudness))
                },
                onClick = { onJrLoudnessChange(!jrLoudness) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.graphic_eq),
                title = { Text("Excitador armónico") },
                description = {
                    Column {
                        Text("Añade aire y presencia en altas frecuencias")
                        if (jrExciter) Slider(value = jrExciterAmt, onValueChange = onJrExciterAmtChange, valueRange = 0f..1f)
                    }
                },
                trailingContent = {
                    Switch(checked = jrExciter, onCheckedChange = onJrExciterChange, thumbContent = thumb(jrExciter))
                },
                onClick = { onJrExciterChange(!jrExciter) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.graphic_eq),
                title = { Text("Amplitud estéreo") },
                description = {
                    Column {
                        Text("Imagen estéreo medio/lados (1.0 = original)")
                        if (jrStereo) Slider(value = jrStereoAmt, onValueChange = onJrStereoAmtChange, valueRange = 0f..2f)
                    }
                },
                trailingContent = {
                    Switch(checked = jrStereo, onCheckedChange = onJrStereoChange, thumbContent = thumb(jrStereo))
                },
                onClick = { onJrStereoChange(!jrStereo) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.graphic_eq),
                title = { Text("Realce de diálogos") },
                description = {
                    Column {
                        Text("Mejora la claridad vocal (centro 300 Hz – 3 kHz)")
                        if (jrDialogue) Slider(value = jrDialogueAmt, onValueChange = onJrDialogueAmtChange, valueRange = 0f..1f)
                    }
                },
                trailingContent = {
                    Switch(checked = jrDialogue, onCheckedChange = onJrDialogueChange, thumbContent = thumb(jrDialogue))
                },
                onClick = { onJrDialogueChange(!jrDialogue) },
            ),
        ),
    )
}

@Composable
private fun EqCurvePreview(bandGains: FloatArray, enabled: Boolean) {
    val base = MaterialTheme.colorScheme.primary
    val curveColor = if (enabled) base else base.copy(alpha = 0.35f)
    val fillColor = curveColor.copy(alpha = 0.15f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val maxGain = iad1tya.echo.music.eq.data.EqConstants.GAIN_MAX
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
        line(0.5f, 0.6f)
        line(0.12f, 0.25f)
        line(0.88f, 0.25f)

        val n = bandGains.size
        if (n < 2) return@Canvas
        fun px(i: Int) = w * i / (n - 1).toFloat()
        fun py(g: Float) = (mid - (g / maxGain) * (mid * 0.85f)).coerceIn(0f, h)

        val curve = androidx.compose.ui.graphics.Path()
        val fill = androidx.compose.ui.graphics.Path()
        curve.moveTo(px(0), py(bandGains[0]))
        fill.moveTo(px(0), mid)
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
        fill.lineTo(px(n - 1), mid)
        fill.close()
        drawPath(fill, fillColor)
        drawPath(
            curve,
            curveColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()),
        )
    }
}

@Composable
private fun DeviceEqDialog(
    customProfiles: List<iad1tya.echo.music.eq.data.SavedEQProfile>,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val outputs = remember { iad1tya.echo.music.eq.data.EqDeviceProfileStore.connectedOutputs(context) }
    val assignments = remember {
        androidx.compose.runtime.mutableStateMapOf<String, String?>().apply {
            outputs.forEach {
                put(it.key, iad1tya.echo.music.eq.data.EqDeviceProfileStore.assignedProfileId(context, it.key))
            }
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("EQ por dispositivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Asigna un perfil de EQ a cada salida. Se aplicará solo cuando se conecte ese dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (customProfiles.isEmpty()) {
                    Text("Primero guarda al menos un perfil de EQ.", style = MaterialTheme.typography.bodyMedium)
                }
                outputs.forEach { out ->
                    var expanded by remember { mutableStateOf(false) }
                    val selectedName = customProfiles.firstOrNull { it.id == assignments[out.key] }?.name ?: "Ninguno"
                    Column {
                        Text(
                            out.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                enabled = customProfiles.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                            ) { Text(selectedName) }
                            androidx.compose.material3.DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Ninguno") },
                                    onClick = {
                                        assignments[out.key] = null
                                        iad1tya.echo.music.eq.data.EqDeviceProfileStore.assign(context, out.key, null)
                                        expanded = false
                                    },
                                )
                                customProfiles.forEach { p ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(p.name) },
                                        onClick = {
                                            assignments[out.key] = p.id
                                            iad1tya.echo.music.eq.data.EqDeviceProfileStore.assign(context, out.key, p.id)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Listo") }
        },
    )
}

@Composable
private fun PreampCard(preamp: Float, enabled: Boolean, onPreampChange: (Float) -> Unit, onCommit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Preamplificador", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                text = "%+.1f dB".format(preamp),
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
        }
        Slider(
            value = preamp,
            onValueChange = onPreampChange,
            onValueChangeFinished = onCommit,
            valueRange = EqConstants.PREAMP_MIN..EqConstants.PREAMP_MAX,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FactoryPresetRow(
    bandGains: FloatArray,
    enabled: Boolean,
    onPresetClick: (FactoryPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Presets",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FactoryPreset.entries.forEach { preset ->
                val selected = bandGains.size == preset.gains.size &&
                    bandGains.indices.all { abs(bandGains[it] - preset.gains[it]) < 0.5f }
                FilterChip(
                    selected = selected,
                    onClick = { if (enabled) onPresetClick(preset) },
                    enabled = enabled,
                    label = { Text(preset.displayName) },
                )
            }
        }
    }
}

@Composable
private fun BandEqCard(
    bandGains: FloatArray,
    enabled: Boolean,
    onBandChange: (Int, Float) -> Unit,
    onBandCommit: () -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (band in 0 until EqConstants.BAND_COUNT) {
                    EqBandSlider(
                        label = EqConstants.FREQUENCY_LABELS[band],
                        value = bandGains.getOrElse(band) { 0f },
                        enabled = enabled,
                        onValueChange = { onBandChange(band, it) },
                        onValueChangeFinished = onBandCommit,
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
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

/**
 * Parametric (PEQ) editor — 5–8 fully user-defined bands. Each band exposes free frequency / Q / gain
 * plus a peak/shelf type selector. Values are pushed live and committed on focus loss / settle.
 *
 * Superseded by [PeqGraphEditor] (the interactive drag-to-shape graph). Kept for reference; not wired
 * into the PARAMETRIC branch any more.
 */
@Suppress("unused")
@Composable
private fun PeqEditorCard(
    peqBands: List<ParametricEQBand>,
    enabled: Boolean,
    onBandChange: (index: Int, freq: Double?, q: Double?, gain: Double?, type: FilterType?) -> Unit,
    onBandCommit: () -> Unit,
    onAddBand: () -> Unit,
    onRemoveBand: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        peqBands.forEachIndexed { index, band ->
            PeqBandRow(
                index = index,
                band = band,
                enabled = enabled,
                canRemove = enabled && peqBands.size > 5,
                onFreqChange = { onBandChange(index, it, null, null, null) },
                onQChange = { onBandChange(index, null, it, null, null) },
                onGainChange = { onBandChange(index, null, null, it, null) },
                onTypeChange = { onBandChange(index, null, null, null, it) },
                onCommit = onBandCommit,
                onRemove = { onRemoveBand(index) },
            )
            if (index < peqBands.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
        OutlinedButton(
            onClick = onAddBand,
            enabled = enabled && peqBands.size < 8,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Añadir banda")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeqBandRow(
    index: Int,
    band: ParametricEQBand,
    enabled: Boolean,
    canRemove: Boolean,
    onFreqChange: (Double) -> Unit,
    onQChange: (Double) -> Unit,
    onGainChange: (Double) -> Unit,
    onTypeChange: (FilterType) -> Unit,
    onCommit: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Banda ${index + 1}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            androidx.compose.material3.IconButton(
                onClick = onRemove,
                enabled = canRemove,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Quitar banda",
                    tint = if (canRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeqNumberField(
                label = "Frec (Hz)",
                value = band.frequency,
                decimals = 0,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onValueChange = onFreqChange,
                onCommit = onCommit,
            )
            PeqNumberField(
                label = "Q",
                value = band.q,
                decimals = 2,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onValueChange = onQChange,
                onCommit = onCommit,
            )
            PeqNumberField(
                label = "Gan (dB)",
                value = band.gain,
                decimals = 1,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onValueChange = onGainChange,
                onCommit = onCommit,
            )
        }
        PeqTypeSelector(
            selected = band.filterType,
            enabled = enabled,
            onTypeChange = {
                onTypeChange(it)
                onCommit()
            },
        )
    }
}

/**
 * A numeric [OutlinedTextField] bound to a Double, usable on any locale.
 *
 * - Formatting is locale-fixed ([java.util.Locale.US]) so the decimal separator is always '.', and the
 *   parser normalizes ',' → '.' — on a Spanish locale "0,7" and "0.7" both parse, and we never emit a
 *   comma the parser would then reject.
 * - The buffer is a free editable string (NOT keyed on [value]) so keystrokes are never clobbered and
 *   the live clamp can't snap the text mid-typing (you can type "900" even if intermediate "9" clamps).
 * - While focused, edits push the parsed live value to the VM WITHOUT reformatting/clamping the text.
 *   On focus LOSS the model (already clamped by the VM) is reformatted back into the field and committed.
 * - Blank / "." / "-" (incomplete) input is a no-op — it neither pushes nor crashes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeqNumberField(
    label: String,
    value: Double,
    decimals: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (Double) -> Unit,
    onCommit: () -> Unit,
) {
    fun fmt(v: Double): String =
        if (decimals == 0) v.roundToInt().toString()
        else String.format(java.util.Locale.US, "%.${decimals}f", v)

    var text by remember { mutableStateOf(fmt(value)) }
    var focused by remember { mutableStateOf(false) }

    // Resync the displayed text from the (clamped) model only while NOT focused, so external updates
    // (load profile, reset) reflect, but typing is never overwritten.
    LaunchedEffect(value, focused) {
        if (!focused) text = fmt(value)
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            // Push a LIVE value without reformatting/clamping the displayed text. Incomplete tokens
            // (blank, lone "." or "-") are intentionally skipped.
            it.replace(',', '.').toDoubleOrNull()?.let { parsed -> onValueChange(parsed) }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.onFocusChanged { focus ->
            if (focus.isFocused) {
                focused = true
            } else if (focused) {
                focused = false
                // Settle: parse + push final value (VM clamps), persist. The LaunchedEffect above then
                // resyncs `text` to the freshly-clamped model now that focused == false (avoids using a
                // stale captured `value` here).
                text.replace(',', '.').toDoubleOrNull()?.let { parsed -> onValueChange(parsed) }
                onCommit()
            }
        },
    )
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
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier.width(46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "%+d".format(value.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.height(200.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = EqConstants.GAIN_MIN..EqConstants.GAIN_MAX,
                enabled = enabled,
                modifier = Modifier
                    .width(200.dp)
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

    val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 320.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = blockShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.eq_save_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
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
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                    }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagePresetsDialog(
    customProfiles: List<SavedEQProfile>,
    onDismiss: () -> Unit,
    onDeleteSelected: (List<String>) -> Unit,
) {
    val selectedIds = remember { mutableStateListOf<String>() }

    val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 320.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = blockShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.eq_manage_presets),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        if (customProfiles.isEmpty()) {
                            Text(
                                text = stringResource(R.string.eq_no_custom_presets),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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
}
