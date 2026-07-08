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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ForceSplitViewKey
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.constants.SidePanelOnLeftKey
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.DeviceCapabilities
import iad1tya.echo.music.utils.DeviceForm
import iad1tya.echo.music.utils.DeviceTier
import iad1tya.echo.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val (highPerfMode, onHighPerfModeChange) = rememberPreference(HighPerformanceModeKey, defaultValue = false)
    val (forceSplit, onForceSplitChange) = rememberPreference(ForceSplitViewKey, defaultValue = false)
    val (sidePanelLeft, onSidePanelLeftChange) = rememberPreference(SidePanelOnLeftKey, defaultValue = false)

    // Read-only diagnostic: why the mode auto-enabled (LOW hardware / TV / car).
    val detected = remember {
        val tier = DeviceCapabilities.tier(context)
        val form = when {
            DeviceForm.isTelevision(context) -> "Android TV"
            DeviceForm.isCar(context) -> "pantalla de auto"
            else -> null
        }
        val tierLabel = when (tier) {
            DeviceTier.ULTRA -> "gama ultra-baja"
            DeviceTier.LOW -> "gama baja"
            DeviceTier.MID -> "gama media"
            DeviceTier.HIGH -> "gama alta"
        }
        if (form != null) "Dispositivo detectado: $form ($tierLabel)" else "Dispositivo detectado: $tierLabel"
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
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Material3SettingsGroup(
            title = "Rendimiento",
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.speed),
                        title = { Text("Modo alto rendimiento") },
                        description = {
                            Text(
                                "Un único modo que recorta la app al máximo en CUALQUIER dispositivo. Apaga los fondos " +
                                    "animados, el visualizador y el video musical, usa carátulas más pequeñas, muestra menos " +
                                    "carruseles en el inicio, baja la resolución/buffers y desactiva la precarga — solo audio, " +
                                    "fluido y fresco. Se enciende solo en gama baja, Android TV o pantallas de auto, pero podés " +
                                    "activarlo en cualquier equipo. El sonido (ecualizador, volumen seguro, normalización) NO se " +
                                    "ve afectado. Algunos cambios aplican al reiniciar la app."
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = highPerfMode,
                                onCheckedChange = onHighPerfModeChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (highPerfMode) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onHighPerfModeChange(!highPerfMode) }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.speed),
                        title = { Text(detected) },
                        description = {
                            Text("Se activa solo en el primer inicio en dispositivos de gama baja, Android TV o pantallas de auto. Podés activarlo o desactivarlo manualmente cuando quieras.")
                        },
                        onClick = {}
                    )
                )
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = "Pantalla grande",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.speed),
                    title = { Text("Vista dividida estilo Spotify") },
                    description = {
                        Text(
                            "Fuerza el diseño de pantalla ancha (cola/lista a un lado, reproductor y controles al otro) " +
                                "en cualquier dispositivo. Se activa solo en TV, autos, tablets y plegables abiertos; " +
                                "actívalo aquí si lo querés también en tu teléfono u otra pantalla."
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = forceSplit,
                            onCheckedChange = onForceSplitChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (forceSplit) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onForceSplitChange(!forceSplit) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.speed),
                    title = { Text("Panel del reproductor a la izquierda") },
                    description = {
                        Text(
                            "En la vista dividida, mueve la cola/reproductor al lado IZQUIERDO (por defecto va a la " +
                                "derecha). Útil en pantallas de auto donde se prefiere de un lado."
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = sidePanelLeft,
                            onCheckedChange = onSidePanelLeftChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sidePanelLeft) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSidePanelLeftChange(!sidePanelLeft) }
                )
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text("Rendimiento") },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
