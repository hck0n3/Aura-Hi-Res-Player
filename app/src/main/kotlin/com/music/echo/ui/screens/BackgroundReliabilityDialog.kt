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
 * One-time / re-prompt so playback survives screen-off and background on every brand.
 * Aggressive skins (MIUI/HyperOS, ColorOS, One UI, …) freeze/kill a backgrounded media service —
 * which stops music and makes the app "aparecer y desaparecer" in Android Auto. Stock Android can
 * still put Wi‑Fi to sleep with the screen locked; the two real user levers remain: OS battery-
 * optimization exemption, and the app-details page where OEM "Autostart / Inicio automático" lives
 * (no public API to flip autostart). Same actions later from Ajustes → Contenido.
 *
 * [oemKillEvidence]: when true, the copy names HyperOS kills already proven in the owner's
 * exit_reasons (ScreenOffCPUCheckKill / OneKeyClean) so the prompt is not a generic nag.
 */
@Composable
fun BackgroundReliabilityDialog(
    onAllowBattery: () -> Unit,
    onOpenAutostart: () -> Unit,
    onDismiss: () -> Unit,
    oemKillEvidence: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(painterResource(R.drawable.battery_charging), contentDescription = null)
        },
        title = {
            Text(
                if (oemKillEvidence) "Apaga el Ahorro de batería"
                else "Que Aura no se corte con la pantalla apagada",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (oemKillEvidence) {
                        "HyperOS (Xiaomi/China) ha matado Aura en segundo plano " +
                            "(ScreenOffCPU / limpiar / Ahorro de batería). Eso corta Bluetooth y Android Auto " +
                            "aunque la app esté «sin restricciones».\n\n" +
                            "Haz esto YA (lo más importante primero):\n" +
                            "• Apaga el Ahorro de batería del teléfono mientras escuchas en el coche\n" +
                            "• Ajustes de Aura: sin restricciones + Inicio automático / Autostart\n" +
                            "• En recientes: bloquea Aura (candado) y no uses «limpiar» / OneKeyClean"
                    } else {
                        "En muchas marcas el teléfono duerme la red o cierra Aura con la pantalla bloqueada. " +
                            "Eso deja segundos de silencio hasta que desbloqueas, o corta Bluetooth / Android Auto.\n\n" +
                            "Activa estas dos cosas (vale para Xiaomi, Samsung, Oppo, Vivo, Motorola, etc.):"
                    },
                )
                OutlinedButton(
                    onClick = onOpenAutostart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("2) Activar “Inicio automático” / sin restricciones")
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

