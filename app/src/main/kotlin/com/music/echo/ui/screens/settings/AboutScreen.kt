@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.theme.BrandAccent
import iad1tya.echo.music.ui.utils.backToMain

private data class Feature(val icon: Int, val title: String, val subtitle: String)

private val PLAYBACK_FEATURES = listOf(
    Feature(R.drawable.play, "Reproducción", "Sin cortes (gapless), transición suave (crossfade) lineal de 10s, normalización de volumen al mismo nivel en todas las canciones y temporizador de apagado"),
    Feature(R.drawable.videocam, "Video y segundo plano", "Alterna entre solo audio y video musical; sigue sonando en segundo plano y con la pantalla apagada"),
    Feature(R.drawable.graphic_eq, "Sonido y EQ", "Ecualizador de 24 bandas con potenciadores manuales de Graves y Agudos (Tono estilo Poweramp), limitador multibanda anti-distorsión con headroom automático, Auto-EQ por modelo de auricular (+5000), sonoridad tipo TIDAL con limitador true-peak, firma Aura, realce de graves, excitador y ancho estéreo"),
    Feature(R.drawable.tune, "Sonido sin pérdida", "Reproduce en calidad sin pérdida desde Qobuz/Saavn cuando está disponible"),
    Feature(R.drawable.auto_awesome, "Mejorar calidad baja", "Reduce la distorsión (declip) y regenera agudos en fuentes de bajo bitrate"),
    Feature(R.drawable.equalizer, "Visualizador y control", "Visualizador de espectro y control de tempo/tono"),
    Feature(R.drawable.lyrics, "Letras", "Sincronizadas (palabra por palabra), con traducción por IA y desenfoque estilo Apple Music"),
    Feature(R.drawable.queue_music, "Cola", "Cola inteligente y gestión de 'a continuación'"),
    Feature(R.drawable.bluetooth, "Reproducción inteligente", "Pausa al silenciar y reanuda al reconectar Bluetooth; notificación enriquecida estilo Dynamic Island"),
)

private val DISCOVERY_FEATURES = listOf(
    Feature(R.drawable.discover_tune, "Recomendación en el dispositivo", "Una IA local aprende de tu recencia, saltos y hora del día para ordenar Inicio, autoplay, radio y shuffle, sin subir tu perfil"),
    Feature(R.drawable.shuffle, "Smart Shuffle", "Mezcla ponderada por lo que de verdad te gusta"),
    Feature(R.drawable.auto_awesome, "Listas con IA", "Crea playlists describiéndolas con una frase"),
    Feature(R.drawable.favorite_border, "Filtros y coherencia", "El filtro 'No me gusta' aparta canciones de las recomendaciones y mantiene la coherencia de género en autoplay/radio"),
    Feature(R.drawable.music_history, "Release Radar", "Nuevos lanzamientos de los artistas que sigues"),
    Feature(R.drawable.library_music, "Discografía completa", "Catálogos de artista autocompletados, con sección 'Aparece en'"),
    Feature(R.drawable.mic, "Reconocer canción", "Identifica la música que suena a tu alrededor"),
    Feature(R.drawable.ic_search_mic, "Búsqueda por voz", "Busca hablando, además de explorar Charts, Estados de ánimo y Géneros"),
)

private val LIBRARY_FEATURES = listOf(
    Feature(R.drawable.library_music, "Biblioteca y sincronización", "Sincroniza tu contenido de YouTube Music cuando quieras desde Ajustes ▸ Importar: me gusta, álbumes, artistas, suscripciones, playlists y biblioteca (hub manual, tú eliges qué y cuándo)"),
    Feature(R.drawable.download, "Importar y migrar", "Importa de YouTube, Spotify (listas, me gusta y álbumes) y Aura Hi-Res Player (.jrpl.json); migración selectiva propia (elige playlists, todos los artistas y/o todos los presets de EQ) que se importa de forma aditiva sin borrar nada; y re-sigue tus artistas de Spotify"),
    Feature(R.drawable.sync, "Sincronización programada de Spotify", "Elige qué listas mantener al día y con qué frecuencia (diaria o semanal)"),
    Feature(R.drawable.download, "Modo sin conexión", "Descarga canciones, álbumes y playlists con un gestor de descargas dedicado"),
    Feature(R.drawable.queue_music, "Podcasts", "Motor propio (Apple/iTunes + RSS) con progreso, fijado, búsqueda universal y reproducción por URL directa"),
    Feature(R.drawable.folder_managed, "Medios locales", "Reproduce los archivos de música guardados en el dispositivo"),
    Feature(R.drawable.music_history, "Historial y estadísticas", "Tu historial de escucha y estadísticas detalladas"),
    Feature(R.drawable.backup, "Copia con Google Drive", "Respalda y restaura tu biblioteca (build con Google Play Services)"),
)

private val EXTRAS_FEATURES = listOf(
    Feature(R.drawable.home_outlined, "Tu inicio a tu gusto", "Elige tus artistas y géneros al empezar; el inicio se llena solo con tus artistas, lo que escuchas y tus favoritos, y YouTube recomienda en base a eso"),
    Feature(R.drawable.group_outlined, "Escuchar juntos", "Escucha sincronizada en tiempo real con amigos"),
    Feature(R.drawable.palette, "Temas y fondos", "Material You, modo oscuro puro AMOLED, acento dinámico y Canvas animado del artista y del álbum"),
    Feature(R.drawable.tune, "Opciones de pantalla", "Escala de densidad, alta tasa de refresco, ocultar miniatura/videos/Shorts y recortar carátula"),
    Feature(R.drawable.share, "Compartir y widget", "Comparte con enlaces de YouTube Music y controla la música desde el widget de vinilo"),
    Feature(R.drawable.ic_ringtone, "Establecer como tono", "Usa cualquier canción como tono de tu dispositivo"),
    Feature(R.drawable.play, "Android Auto y Android TV", "Compatible con Android Auto en el coche y con Android TV (se instala y navega con el control en televisores)"),
    Feature(R.drawable.cast, "Google Cast", "Envía el audio a dispositivos Chromecast (build con Google Play Services)"),
    Feature(R.drawable.notification, "Fiabilidad en segundo plano", "Exención opcional de batería contra apps que matan procesos y reinicio limpio tras iniciar sesión con Google"),
    Feature(R.drawable.download, "Actualizaciones", "Auto-actualización sin desinstalar y aviso semanal cuando hay una versión nueva"),
    Feature(R.drawable.auto_awesome, "Suscripción y demo", "Prueba gratis de 3 días y suscripción mensual"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onBack?.invoke() ?: navController.navigateUp() },
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { AboutAppCard() }

            item {
                AboutSectionCard(title = "Reproducción y audio") {
                    FeatureList(PLAYBACK_FEATURES)
                }
            }
            item {
                AboutSectionCard(title = "Descubrimiento e IA") {
                    FeatureList(DISCOVERY_FEATURES)
                }
            }
            item {
                AboutSectionCard(title = "Biblioteca y contenido") {
                    FeatureList(LIBRARY_FEATURES)
                }
            }
            item {
                AboutSectionCard(title = "Personalización y más") {
                    FeatureList(EXTRAS_FEATURES)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FeatureList(features: List<Feature>) {
    features.forEachIndexed { index, feature ->
        AboutFeatureRow(
            icon = painterResource(feature.icon),
            title = feature.title,
            subtitle = feature.subtitle,
        )
        if (index != features.lastIndex) AboutDivider()
    }
}

@Composable
private fun AboutAppCard() {
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
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Light,
                        )
                    ) {
                        append("AURA ")
                    }
                    withStyle(
                        SpanStyle(
                            color = BrandAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    ) {
                        append("HI-RES")
                    }
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 26.sp,
                    letterSpacing = 6.sp,
                ),
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                if (BuildConfig.DEBUG) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = "DEBUG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = BuildConfig.ARCHITECTURE.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSectionCard(
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
private fun AboutFeatureRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = tint.copy(alpha = 0.10f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}
