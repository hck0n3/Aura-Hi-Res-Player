package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.newui.AuraDialogWindowEffects
import iad1tya.echo.music.ui.newui.AuraFloatingSurface
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.theme.BrandAccent

private data class WelcomeTourPage(
    val iconRes: Int,
    val title: String,
    val body: String,
)

/**
 * First-run / version-bump welcome. With Interfaz nueva ON it is a short tour of where things live
 * and how to switch back to the classic look — owner launch request.
 */
@Composable
fun WelcomeDialog(
    onDismissRequest: () -> Unit,
) {
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    val pages = remember {
        listOf(
            WelcomeTourPage(
                iconRes = R.drawable.music_note,
                title = "Nueva interfaz Aura",
                body = "Esta versión estrena la apariencia premium: Inicio, Biblioteca, Buscar y el " +
                    "reproductor comparten la misma piel oscura con tipografía y portadas más claras.",
            ),
            WelcomeTourPage(
                iconRes = R.drawable.home_outlined,
                title = "Dónde está todo",
                body = "Barra inferior: Inicio · Buscar · Biblioteca · Ajustes. Arriba a la derecha: " +
                    "cuenta (avatar), historial y atajos. El mini-reproductor flota encima de la barra.",
            ),
            WelcomeTourPage(
                iconRes = R.drawable.play,
                title = "Reproductor",
                body = "Toca el mini para expandir. Abajo: Letras, cola, dispositivos y Más. " +
                    "Los vídeos se abren en modo vídeo al tocarlos. Cast sigue arriba a la derecha.",
            ),
            WelcomeTourPage(
                iconRes = R.drawable.tune,
                title = "¿Prefieres la anterior?",
                body = "En Ajustes (o en la hoja de cuenta ▸ Ajustes) desactiva «Interfaz nueva». " +
                    "Vuelves a la apariencia clásica al instante, sin perder tu música ni tu cola.",
            ),
        )
    }
    var page by remember { mutableIntStateOf(0) }
    val current = pages[page]
    val isLast = page == pages.lastIndex

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (premium) {
            AuraDialogWindowEffects(enabled = true)
            AuraFloatingSurface(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                shape = AuraShapes.Card,
            ) {
                WelcomeTourBody(
                    current = current,
                    page = page,
                    pageCount = pages.size,
                    isLast = isLast,
                    premium = true,
                    onNext = { if (isLast) onDismissRequest() else page++ },
                    onSkip = onDismissRequest,
                    onBack = { if (page > 0) page-- },
                )
            }
        } else {
            androidx.compose.material3.Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                WelcomeTourBody(
                    current = current,
                    page = page,
                    pageCount = pages.size,
                    isLast = isLast,
                    premium = false,
                    onNext = { if (isLast) onDismissRequest() else page++ },
                    onSkip = onDismissRequest,
                    onBack = { if (page > 0) page-- },
                )
            }
        }
    }
}

@Composable
private fun WelcomeTourBody(
    current: WelcomeTourPage,
    page: Int,
    pageCount: Int,
    isLast: Boolean,
    premium: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val titleColor = if (premium) AuraPalette.OnGround else MaterialTheme.colorScheme.onSurface
    val muted = if (premium) AuraPalette.OnGroundMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (premium) AuraPalette.Teal else BrandAccent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Aura Hi-Res Player",
            style = if (premium) AuraType.SheetTitle else MaterialTheme.typography.titleLarge,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = BuildConfig.VERSION_NAME,
            style = if (premium) AuraType.Technical else MaterialTheme.typography.labelMedium,
            color = muted,
        )

        Icon(
            painter = painterResource(current.iconRes),
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(40.dp),
        )
        Text(
            text = current.title,
            style = if (premium) AuraType.RowTitle else MaterialTheme.typography.titleMedium,
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = current.body,
            style = if (premium) AuraType.CalloutSubtitle else MaterialTheme.typography.bodyMedium,
            color = muted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Text(
            text = "${page + 1} / $pageCount",
            style = if (premium) AuraType.Technical else MaterialTheme.typography.labelSmall,
            color = muted,
        )

        Box(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (page == 0) onSkip else onBack) {
                Text(
                    text = if (page == 0) "Omitir" else "Atrás",
                    color = muted,
                )
            }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = if (premium) AuraPalette.Ground else MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(if (isLast) "Empezar" else "Siguiente")
            }
        }
    }
}
