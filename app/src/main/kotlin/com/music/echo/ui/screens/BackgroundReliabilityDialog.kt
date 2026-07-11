package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R

/**
 * One-time prompt shown on aggressive OEM skins (MIUI/HyperOS, ColorOS, One UI, …) that freeze/kill a
 * backgrounded media service — which is what stops background playback and makes the app "aparecer y
 * desaparecer" in Android Auto. Offers the two real levers: OS battery-optimization exemption, and a
 * shortcut to the app-details page where the OEM "Autostart / Inicio automático" toggle lives (no public
 * API exists to flip autostart directly). Everything the user can also do later from Ajustes → Contenido.
 */
@Composable
fun BackgroundReliabilityDialog(
    onAllowBattery: () -> Unit,
    onOpenAutostart: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(painterResource(R.drawable.battery_charging), contentDescription = null)
        },
        title = { Text("Que Aura no se corte en segundo plano") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Tu teléfono puede cerrar Aura cuando está en segundo plano. Eso corta la música y hace " +
                        "que la app aparezca y desaparezca en Android Auto.\n\nActiva estas dos cosas para que " +
                        "no vuelva a pasar:",
                )
                OutlinedButton(
                    onClick = onOpenAutostart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("2) Activar “Inicio automático”")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAllowBattery) {
                Text("1) Permitir batería sin restricción")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Ahora no")
            }
        },
    )
}
