package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AudioEnhanceEnabledKey
import iad1tya.echo.music.constants.AudioNormalizationKey
import iad1tya.echo.music.constants.AuraSignatureToneEnabledKey
import iad1tya.echo.music.constants.SpectrumVisualizerEnabledKey
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.utils.rememberPreference

/**
 * Dedicated, ordered "Sonido" hub: equalizer, Auto-EQ, all DSP effects/plugins, loudness and the
 * spectrum visualizer in one place — extracted from PlayerSettings (which now keeps only playback).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (auraSignature, onAuraSignatureChange) =
        rememberPreference(AuraSignatureToneEnabledKey, defaultValue = true)
    val (audioNormalization, onAudioNormalizationChange) =
        rememberPreference(AudioNormalizationKey, defaultValue = true)
    val (audioEnhance, onAudioEnhanceChange) =
        rememberPreference(AudioEnhanceEnabledKey, defaultValue = false)
    val (spectrumVisualizer, onSpectrumVisualizerChange) =
        rememberPreference(SpectrumVisualizerEnabledKey, defaultValue = false)

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

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // ── Ecualizador y Auto-EQ ──
        Material3SettingsGroup(
            title = "Ecualizador",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.echoequlizer),
                    title = { Text(stringResource(R.string.echo_equalizer)) },
                    description = { Text(stringResource(R.string.echo_equalizer_desc)) },
                    onClick = { navController.navigate("settings/equalizer") },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text("Auto-EQ (por auricular)") },
                    description = { Text("Busca tu modelo y aplica su perfil AutoEq") },
                    onClick = { navController.navigate("settings/sound/autoeq") },
                ),
            ),
        )

        Spacer(Modifier.height(20.dp))

        // ── Volumen / masterización ──
        Material3SettingsGroup(
            title = "Volumen",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text("Firma Aura (cuerpo + aire)") },
                    description = { Text("Curva suave: un poco más de graves y de agudos para más cuerpo y sensación de calidad. Sin distorsionar.") },
                    trailingContent = {
                        Switch(
                            checked = auraSignature,
                            onCheckedChange = onAuraSignatureChange,
                            thumbContent = thumb(auraSignature),
                        )
                    },
                    onClick = { onAuraSignatureChange(!auraSignature) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_up),
                    title = { Text(stringResource(R.string.audio_normalization)) },
                    description = { Text("Lleva todas las canciones al mismo volumen (−14 LUFS) con limitador true-peak; sube las flojas sin distorsionar.") },
                    trailingContent = {
                        Switch(
                            checked = audioNormalization,
                            onCheckedChange = onAudioNormalizationChange,
                            thumbContent = thumb(audioNormalization),
                        )
                    },
                    onClick = { onAudioNormalizationChange(!audioNormalization) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.auto_awesome),
                    title = { Text(stringResource(R.string.audio_enhance_low_quality)) },
                    description = { Text(stringResource(R.string.audio_enhance_low_quality_desc)) },
                    trailingContent = {
                        Switch(
                            checked = audioEnhance,
                            onCheckedChange = onAudioEnhanceChange,
                            thumbContent = thumb(audioEnhance),
                        )
                    },
                    onClick = { onAudioEnhanceChange(!audioEnhance) },
                ),
            ),
        )

        Spacer(Modifier.height(20.dp))

        // ── Efectos DSP ──
        Material3SettingsGroup(
            title = "Efectos DSP",
            items = listOf(
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

        Spacer(Modifier.height(20.dp))

        // ── Visualizador ──
        Material3SettingsGroup(
            title = "Visualizador",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.spectrum_visualizer)) },
                    description = { Text(stringResource(R.string.spectrum_visualizer_desc)) },
                    trailingContent = {
                        Switch(checked = spectrumVisualizer, onCheckedChange = onSpectrumVisualizerChange, thumbContent = thumb(spectrumVisualizer))
                    },
                    onClick = { onSpectrumVisualizerChange(!spectrumVisualizer) },
                ),
            ),
        )

        Spacer(Modifier.height(27.dp))
    }
}
