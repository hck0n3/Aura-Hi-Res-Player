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
import iad1tya.echo.music.constants.SafeVolumeEnabledKey
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

        // ── Volumen ──
        val (safeVolume, onSafeVolumeChange) = rememberPreference(SafeVolumeEnabledKey, defaultValue = false)
        Material3SettingsGroup(
            title = "Volumen",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_up),
                    title = { Text("Volumen seguro") },
                    description = {
                        Text(
                            "Baja los temas muy fuertes a un nivel parejo y agrega un limitador suave, " +
                                "para que no distorsionen a todo volumen. Apagado = reproducción bit-perfect Hi-Res (por defecto)."
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = safeVolume,
                            onCheckedChange = onSafeVolumeChange,
                            thumbContent = thumb(safeVolume),
                        )
                    },
                    onClick = { onSafeVolumeChange(!safeVolume) },
                ),
            ),
        )

        Spacer(Modifier.height(27.dp))
    }
}
