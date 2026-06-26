package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.theme.BrandAccent

@Composable
fun WelcomeDialog(
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WelcomeHeader()

                WelcomeSectionCard(title = "Todo lo que puedes hacer") {
                    WelcomeFeatureRow(
                        iconRes = R.drawable.music_note,
                        title = "Música ilimitada",
                        subtitle = "Todo el catálogo de YouTube Music, gratis y sin anuncios"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.login,
                        title = "Tu biblioteca: YouTube Music y Spotify",
                        subtitle = "Sincroniza tu contenido de YouTube Music (me gusta, álbumes, artistas, suscripciones y playlists) desde Ajustes ▸ Importar —manual o programada (diaria/semanal), con la hora de la última sincronización a la vista— e importa tus listas, me gusta y álbumes de Spotify"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.equalizer,
                        title = "Ecualizador gráfico (24) + paramétrico (PEQ)",
                        subtitle = "EQ gráfico de 24 bandas (las que dejes en 0 dB no procesan nada — señal pura) o modo paramétrico de 5-8 bandas con frecuencia/Q/ganancia exactas, limitador multibanda anti-distorsión, JR DSP y perfiles AutoEq para +5000 auriculares que bloquean el EQ manual para una corrección perfecta"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.tune,
                        title = "Audio de alta calidad",
                        subtitle = "Sonido sin pérdida (Qobuz/Saavn), normalización de volumen, visualizador de espectro y control de tempo/tono"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.lyrics,
                        title = "Letras sincronizadas",
                        subtitle = "Letras en tiempo real con múltiples proveedores"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.download,
                        title = "Modo sin conexión",
                        subtitle = "Descarga canciones y playlists para escucharlas offline"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.share,
                        title = "Compartir directo",
                        subtitle = "Links directos de YouTube Music al compartir música"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.palette,
                        title = "Temas a tu estilo",
                        subtitle = "Acento dinámico, Material You y modo oscuro puro AMOLED"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.music_note,
                        title = "Fondos animados (Canvas)",
                        subtitle = "Video del artista y del álbum de fondo mientras suena tu música"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.speed,
                        title = "Se adapta a tu teléfono",
                        subtitle = "Detecta la potencia del dispositivo (RAM/CPU) y ajusta los efectos; el fondo animado se pausa con la app en segundo plano o la pantalla apagada, para no calentar ni gastar batería. En plegables (Z Fold), al desplegar, el ecualizador y los efectos DSP se ven en dos columnas a la vez"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.group,
                        title = "Listen Together",
                        subtitle = "Escucha música en tiempo real con tus amigos"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.discover_tune,
                        title = "Recomendaciones con IA",
                        subtitle = "Inicio, radio y mezclas que aprenden de tus gustos y de toda tu biblioteca importada (en el dispositivo); al acabar una lista la música sigue sola con temas similares, el modo aleatorio no repite hasta agotar, y crea listas con una frase"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.videocam,
                        title = "Video musical",
                        subtitle = "Mira el videoclip con sonido, sigue en video entre canciones; pantalla completa al girar, ventana flotante (Picture-in-Picture) y sección 'Videos oficiales' en cada artista"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.mic,
                        title = "Reconocer canción y buscar por voz",
                        subtitle = "Identifica lo que suena a tu alrededor (desde la app, el widget o Ajustes Rápidos) y busca hablando"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.queue_music,
                        title = "Podcasts",
                        subtitle = "Motor propio de podcasts con progreso, fijado y búsqueda"
                    )
                }

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Cerrar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun WelcomeHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "¡Bienvenido a",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Light
                        )
                    ) {
                        append("AURA ")
                    }
                    withStyle(
                        SpanStyle(
                            color = BrandAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append("HI-RES")
                    }
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 26.sp,
                    letterSpacing = 6.sp
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tu música, tu sonido, tu estilo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun WelcomeSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun WelcomeFeatureRow(
    iconRes: Int,
    title: String,
    subtitle: String,
) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(12.dp),
            color = tint.copy(alpha = 0.10f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = tint,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WelcomeDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}
