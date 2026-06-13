package iad1tya.echo.music.ui.screens.equalizer.axion

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import iad1tya.echo.music.R
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FactoryPreset
import iad1tya.echo.music.eq.data.SavedEQProfile
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import kotlin.math.abs
import kotlin.math.roundToInt
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
    val isDirty by viewModel.isDirty.collectAsState()
    val customProfiles by viewModel.customProfiles.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

            PreampCard(preamp = preamp, enabled = enabled, onPreampChange = { viewModel.setPreamp(it) })

            FactoryPresetRow(
                bandGains = bandGains,
                enabled = enabled,
                onPresetClick = { viewModel.applyPreset(it) },
            )

            BandEqCard(
                bandGains = bandGains,
                enabled = enabled,
                onBandChange = { i, v -> viewModel.setBandGain(i, v) },
                onReset = { viewModel.reset() },
            )

            AnimatedVisibility(
                visible = isDirty && enabled,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                OutlinedButton(
                    onClick = { showSaveDialog = true },
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
                    onApply = { viewModel.setBandsGains(it) },
                    onEditClick = { showManageDialog = true },
                )
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
private fun PreampCard(preamp: Float, enabled: Boolean, onPreampChange: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pre-Amp", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                text = "%+.1f dB".format(preamp),
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
        }
        Slider(
            value = preamp,
            onValueChange = onPreampChange,
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
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = onReset) {
                Icon(Icons.Rounded.Replay, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.eq_reset))
            }
        }
    }
}

@Composable
private fun EqBandSlider(
    label: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
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
    onApply: (FloatArray) -> Unit,
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
                    onClick = { if (enabled) onApply(gains) },
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
