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
                        subtitle = "Todo el catálogo de YouTube Music, sin anuncios; reproductor estilo YouTube Music con Me gusta / No me gusta en una píldora dividida abajo (los pulgares empiezan en contorno y solo se rellena el que eliges), cola al deslizar hacia arriba con pestañas SIGUIENTE / LETRA / RELACIONADOS y reproducción automática con chips al final de la cola"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.login,
                        title = "Tu biblioteca: YouTube Music y Spotify",
                        subtitle = "Sincroniza tu contenido de YouTube Music (me gusta, álbumes, artistas, suscripciones y playlists) desde Ajustes ▸ Importar —manual o programada (diaria/semanal), con la hora de la última sincronización a la vista— e importa tus listas, me gusta y álbumes de Spotify, o pega el link de cualquier lista pública de Spotify para importarla"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.equalizer,
                        title = "EQ gráfico (10) + paramétrico interactivo (PEQ)",
                        subtitle = "EQ gráfico de 10 bandas (las que dejes en 0 dB no procesan nada — señal pura) o modo paramétrico interactivo: arrastra puntos en la curva para dar forma al sonido (5-8 bandas, con frecuencia/Q/ganancia exactas para el purista), limitador anti-distorsión con headroom automático y perfiles AutoEq para +5000 auriculares que se combinan con tu EQ manual (cascada); de fábrica viene activo un perfil audiófilo con preamp 0 dB"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.tune,
                        title = "Audio de alta calidad",
                        subtitle = "Cadena de audio en 32-bit float, bit-perfect (lo que no actives no toca la señal); sonido sin pérdida (Qobuz, con Saavn como respaldo a 320 kbps), Volumen Seguro activado por defecto (nivela y limita las pistas muy altas; se puede apagar) y control de tempo/tono"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.play,
                        title = "Transiciones de audio",
                        subtitle = "Crossfade de 5s estilo Ascenso activado por defecto: las dos canciones suenan juntas (la saliente siempre baja mientras entra la nueva), con 9 curvas y duración 1-15s a elegir; memoriza los silencios de cada canción para que el fundido cubra música —nunca silencio— y entrada suave al cambiar de canción a mano"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.shuffle,
                        title = "Aleatorio Mejorado",
                        subtitle = "No repite nada hasta agotar la lista y recuerda entre días qué oíste en cada playlist o en tu biblioteca; empieza por lo que aún no has escuchado, separa a los artistas para que no te bombardee el mismo, y al agotar la lista sigue solo con la radio infinita"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.skip_next,
                        title = "Saltar partes sin música (SponsorBlock)",
                        subtitle = "Activado por defecto: la app salta sola patrocinios, autopromo y las partes sin música de los videos —usando la base comunitaria SponsorBlock—; nunca corta el audio real de la canción y puedes apagarlo en Ajustes ▸ Reproductor"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.lyrics,
                        title = "Letras sincronizadas",
                        subtitle = "Letras en tiempo real con múltiples proveedores y estilos Apple o Metro, romanización en 12 idiomas, traducción por IA y ajuste de desfase que se guarda por canción"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.download,
                        title = "Modo sin conexión",
                        subtitle = "Descarga canciones y playlists para escucharlas offline —con descarga por bloques que esquiva la limitación de velocidad—; el interruptor de la barra superior cambia entre online/offline con un toque, y un Modo ahorro de datos opcional fuerza Opus y apaga videos, canvas y precarga para gastar el mínimo"
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
                        subtitle = "Acento dinámico, Material You, modo oscuro puro AMOLED y Liquid Glass (Beta): estilo de cristal para reproductor, mini-reproductor, barra de navegación y botones flotantes — se enciende solo en equipos de gama alta capaces; en el resto puedes activarlo en Ajustes ▸ Apariencia"
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
                        subtitle = "Detecta la potencia del dispositivo (RAM/CPU) y ajusta los efectos; además el Modo Rendimiento (ULTRA) fuerza el modo más ligero posible para gama baja, Android TV o auto (sin tocar la calidad de audio). El fondo animado se pausa con la app en segundo plano o la pantalla apagada, para no calentar ni gastar batería. En plegables (Z Fold), al desplegar, el ecualizador aprovecha todo el ancho de la pantalla"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.group,
                        title = "Listen Together",
                        subtitle = "Escucha música en tiempo real con tus amigos, en salas con chat integrado"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.discover_tune,
                        title = "Recomendaciones con IA",
                        subtitle = "Inicio, radio y mezclas que aprenden de tus gustos y de toda tu biblioteca importada (en el dispositivo); Inicio con 'Reproducido recientemente', 'Nuevos lanzamientos', 'Mix diario 1/2/3' y un mix según la hora del día; toca un chip de estado de ánimo y el contenido y la reproducción se sesgan a ese ánimo mientras esté activo; al acabar una playlist o álbum la app analiza sus géneros y artistas y sigue sola en el mismo estilo, y la cola infinita no repite lo recién escuchado; crea listas con una frase, con el número exacto de canciones que pides y respetando el género/idioma/ánimo; y si lo activas en Ajustes ▸ IA, una playlist 'Recomendado para ti (IA)' persistente en Inicio con canciones nuevas según tu historial, refrescada a diario — todo con una cascada de modelos de IA gratuitos que nunca te deja en 'IA ocupada'"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.add,
                        title = "Agregar música a tus playlists",
                        subtitle = "Al final de tus playlists: canciones sugeridas según su contenido (con preview y botón +), artistas destacados y un botón 'Agregar música' con búsqueda global, Desde Replay, Agregado recientemente y selección múltiple de tu biblioteca — estilo Apple Music"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.videocam,
                        title = "Video musical",
                        subtitle = "Mira el videoclip con sonido, sigue en video entre canciones; pantalla completa al girar, ventana flotante (Picture-in-Picture) y sección 'Videos oficiales' en cada artista"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.image,
                        title = "Modo Ambiente",
                        subtitle = "Vista a pantalla completa en horizontal: portada en grande con resplandor animado, la letra y la pantalla siempre encendida; se abre desde el menú del reproductor"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.mic,
                        title = "Reconocer canción y buscar por voz",
                        subtitle = "Identifica lo que suena a tu alrededor con un botón dedicado siempre visible: tócalo y empieza a reconocer al instante (desde la app, el widget o Ajustes Rápidos) — el resultado se reproduce directamente y puedes marcarlo favorito o agregarlo a una playlist ahí mismo — y busca hablando, también en Android TV; la búsqueda suma un filtro 'Videos'"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.queue_music,
                        title = "Podcasts",
                        subtitle = "Motor propio de podcasts con progreso, fijado y búsqueda"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.folder_managed,
                        title = "Música local",
                        subtitle = "Reproduce los archivos de música guardados en tu dispositivo, con escáner de medios locales"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.new_release,
                        title = "Radar de novedades",
                        subtitle = "Los estrenos de los artistas que sigues, renovados cada viernes al estilo Spotify: solo la tanda de la semana"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.library_music,
                        title = "Discografías completas",
                        subtitle = "Los catálogos de artista se completan solos: la app resuelve el canal real del artista y cruza iTunes y listas de la comunidad para mostrar la discografía completa, con todas las colaboraciones"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.grid_view,
                        title = "Pantallas grandes (estilo Spotify)",
                        subtitle = "En tablets, Android TV y plegables la app usa una interfaz dividida (lista + detalle) que aprovecha todo el ancho"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.cast,
                        title = "En el coche, la TV y tu pantalla de inicio",
                        subtitle = "Android Auto, Android TV y Google Cast (build con Google Play Services); tres widgets para tu pantalla de inicio —reproductor, tocadiscos de vinilo y playlists— y copia de seguridad local de tu biblioteca cuando quieras"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.image,
                        title = "Carátula estilo Apple Music",
                        subtitle = "Al cambiar de canción la carátula hace un zoom y fundido dinámico estilo Apple Music"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.account,
                        title = "Cuentas y búsqueda rápida",
                        subtitle = "Un apartado Cuentas reúne YouTube Music, Spotify, Last.fm y ListenBrainz, con buscadores en Ajustes y en tu Biblioteca (artistas seguidos y canciones)"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.sync,
                        title = "Scrobbling (Last.fm y ListenBrainz)",
                        subtitle = "Registra lo que escuchas; opcional y apagado por defecto, no envía nada hasta que conectas tu cuenta en Ajustes ▸ Scrobbling"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.library_music,
                        title = "Resumen de escucha",
                        subtitle = "En Estadísticas: el tiempo total que has escuchado (del periodo y de siempre) y cuántas canciones, artistas y álbumes distintos"
                    )
                    WelcomeDivider()
                    WelcomeFeatureRow(
                        iconRes = R.drawable.ic_ringtone,
                        title = "Extras del reproductor",
                        subtitle = "Pon cualquier canción como tono del dispositivo y recarga el audio en Opus desde el menú del reproductor"
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
