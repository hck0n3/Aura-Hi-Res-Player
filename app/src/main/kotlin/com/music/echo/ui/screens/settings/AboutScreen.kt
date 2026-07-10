@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    Feature(R.drawable.play, "Reproducción", "Sin cortes (gapless), transición suave (crossfade) equal-power de 9s, Volumen Seguro que nivela las canciones muy altas (atenúa, con limitador) y temporizador de apagado"),
    Feature(R.drawable.videocam, "Video musical", "Reproduce el videoclip con su sonido dentro del reproductor (cuando está disponible), sigue en video al cambiar de canción; pantalla completa al girar el teléfono, Picture-in-Picture (ventana flotante) y cambio rápido entre audio y video (con conexión pre-calentada al abrir el reproductor, en wifi y equipos capaces). El audio sigue en segundo plano y con la pantalla apagada"),
    Feature(R.drawable.graphic_eq, "Sonido y EQ", "Ecualizador gráfico de 10 bandas (las que dejes en 0 dB hacen bypass real — no tocan la señal) o modo paramétrico (PEQ) interactivo: arrastra puntos en la curva de respuesta para dar forma al sonido (5-8 bandas, con frecuencia/Q/ganancia exactas), limitador anti-distorsión con headroom automático y Auto-EQ por modelo de auricular (+5000, con catálogo que abre al instante) que se combina con tu EQ manual (cascada)"),
    Feature(R.drawable.volume_up, "Volumen Seguro", "Activado por defecto: nivela las canciones muy altas a un volumen parejo y protege con un limitador true-peak, para que ninguna pista salte de golpe. Puedes desactivarlo cuando quieras en Ajustes ▸ Sonido"),
    Feature(R.drawable.tune, "Sonido sin pérdida", "Reproduce en calidad sin pérdida desde Qobuz cuando está disponible, con Saavn como respaldo a 320 kbps"),
    Feature(R.drawable.refresh, "Recargar en Opus", "Desde el menú del reproductor, vuelve a cargar el audio de la canción actual en Opus si un stream viene con fallos o quieres refrescarlo"),
    Feature(R.drawable.equalizer, "Control de tempo y tono", "Ajusta la velocidad y el tono de la reproducción de forma independiente desde el menú del reproductor"),
    Feature(R.drawable.lyrics, "Letras", "Sincronizadas (palabra por palabra), con traducción por IA y desenfoque estilo Apple Music"),
    Feature(R.drawable.queue_music, "Reproductor y cola estilo YouTube Music", "Pulgares Me gusta / No me gusta compactos junto al título (el 'No me gusta' es conmutable), desliza hacia arriba en el reproductor para abrir la cola y, al final de la cola, reproducción automática con interruptor y chips deslizables para dirigir lo que sigue (relacionado, artistas, mixes). Cola inteligente y gestión de 'a continuación'; tocar una canción en el top de un artista reproduce toda la lista como cola, y en el radar de novedades tocar el play de un estreno lo reproduce completo"),
    Feature(R.drawable.bluetooth, "Reproducción inteligente", "Puede pausar al silenciar y reanudar al reconectar Bluetooth (actívalo en Ajustes ▸ Reproductor); notificación multimedia enriquecida (carátula y controles)"),
    Feature(R.drawable.skip_next, "Saltar partes sin música (SponsorBlock)", "Actívalo en Ajustes ▸ Reproductor y la app salta sola patrocinios, autopromo e interrupciones no musicales usando la base comunitaria SponsorBlock; nunca corta el audio real de la canción (no toca intros/outros)"),
    Feature(R.drawable.speed, "Rendimiento adaptable y Modo Rendimiento (ULTRA)", "Detecta la gama del dispositivo por sus características (RAM, núcleos), no por la marca, y ajusta calidad/buffers de los efectos. El Modo Rendimiento (ULTRA) es un interruptor maestro que fuerza el modo más ligero posible —ideal en gama baja, Android TV o auto— desactivando lo más pesado (Canvas, video del artista) sin tocar la fidelidad de audio; se activa solo en hardware realmente modesto —incluidos TV boxes y pantallas de auto de gama baja; los TVs potentes conservan la experiencia completa— y puedes conmutarlo en Ajustes. El fondo animado (Canvas) se pausa con la app en segundo plano o la pantalla apagada, para no calentar ni gastar batería de más; en plegables el ecualizador aprovecha el ancho al desplegar"),
)

private val DISCOVERY_FEATURES = listOf(
    Feature(R.drawable.discover_tune, "Recomendación en el dispositivo", "Una IA local aprende de tu recencia, saltos, hora del día y de toda tu biblioteca importada para ordenar Inicio, autoplay, radio y shuffle, sin subir tu perfil; al terminar una lista la música sigue sola con temas similares (incluso recién instalada)"),
    Feature(R.drawable.shuffle, "Smart Shuffle", "Mezcla ponderada por lo que de verdad te gusta, con memoria anti-repetición (no repite hasta agotar la lista)"),
    Feature(R.drawable.auto_awesome, "Listas con IA", "Crea playlists describiéndolas con una frase"),
    Feature(R.drawable.favorite_border, "Filtros y coherencia", "El filtro 'No me gusta' aparta canciones de las recomendaciones y mantiene la coherencia de género en autoplay/radio"),
    Feature(R.drawable.music_history, "Release Radar", "Estrenos de los artistas que sigues, renovados cada viernes al estilo Spotify: solo la tanda de la semana; las anteriores desaparecen"),
    Feature(R.drawable.library_music, "Discografía completa", "Catálogos de artista autocompletados, con secciones 'Aparece en' y 'Videos oficiales' (reproducibles)"),
    Feature(R.drawable.mic, "Reconocer canción", "Identifica la música que suena a tu alrededor, desde la app, el widget o un mosaico de Ajustes Rápidos; 'Reproducir con Aura' reproduce el resultado directamente y trae botones de favorito y agregar a playlist ahí mismo"),
    Feature(R.drawable.ic_search_mic, "Búsqueda por voz y filtros", "Busca hablando —también en Android TV, con reconocimiento propio cuando no hay ventana de voz del sistema— además de explorar Charts, Estados de ánimo y Géneros; la búsqueda incluye un filtro 'Videos' con miniaturas panorámicas, y la pestaña 'Todos' respeta tu preferencia de ocultar videos"),
)

private val LIBRARY_FEATURES = listOf(
    Feature(R.drawable.library_music, "Biblioteca y sincronización", "Sincroniza tu contenido de YouTube Music desde Ajustes ▸ Importar: me gusta, álbumes, artistas, suscripciones, playlists y biblioteca — manual o automática (diaria/semanal), con la hora de la última sincronización a la vista"),
    Feature(R.drawable.add, "Agregar música a playlists (Apple Music)", "Al final de tus playlists aparece 'Canciones sugeridas' según el contenido de la lista (con preview sin salir y botón + para agregar al instante, más un icono para regenerarlas) y 'Artistas destacados'. El botón 'Agregar música' abre una ventana con búsqueda global en todo YouTube Music, Desde Replay, Agregado recientemente, más sugeridas y selección múltiple de tu biblioteca para agregar varias de una vez. Al agregar a playlist, las listas a las que agregaste contenido hace poco aparecen primero, y la vista previa de sugeridas carga más rápido"),
    Feature(R.drawable.account, "Cuentas", "Un solo apartado reúne tus servicios conectados —YouTube Music y Spotify— para ver el estado, iniciar sesión o desconectar cada uno desde el mismo sitio"),
    Feature(R.drawable.search, "Buscadores en Biblioteca", "Un buscador dentro de tus artistas seguidos y otro dentro de tus canciones para encontrar al instante lo que ya tienes guardado"),
    Feature(R.drawable.download, "Importar y migrar", "Importa de YouTube, Spotify (listas, me gusta y álbumes — o pega el link de cualquier lista pública de Spotify, aunque no sea tuya) y Aura Hi-Res Player (.jrpl.json); migración selectiva propia (elige playlists, todos los artistas y/o todos los presets de EQ) que se importa de forma aditiva sin borrar nada; y re-sigue tus artistas de Spotify"),
    Feature(R.drawable.sync, "Sincronización programada", "Mantén al día YouTube Music (todo) y las listas de Spotify que elijas, con la frecuencia que prefieras (diaria o semanal)"),
    Feature(R.drawable.download, "Modo sin conexión", "Descarga canciones, álbumes y playlists con un gestor de descargas dedicado; el interruptor de Modo sin conexión de la barra superior cambia entre online/offline con un toque y avisa el estado (Estadísticas ahora vive en Ajustes)"),
    Feature(R.drawable.queue_music, "Podcasts", "Motor propio (Apple/iTunes + RSS) con progreso, fijado, búsqueda universal y reproducción por URL directa; en los podcasts que ofrecen video puedes elegir entre audio y video"),
    Feature(R.drawable.folder_managed, "Medios locales", "Reproduce los archivos de música guardados en el dispositivo"),
    Feature(R.drawable.music_history, "Historial y estadísticas", "Tu historial de escucha y estadísticas detalladas"),
    Feature(R.drawable.backup, "Copia de seguridad local", "Exporta e importa tu biblioteca en un archivo local, cuando quieras y sin depender de la nube"),
)

private val EXTRAS_FEATURES = listOf(
    Feature(R.drawable.home_outlined, "Tu inicio a tu gusto", "Elige tus artistas y géneros al empezar; el inicio se llena solo con tus artistas, lo que escuchas y tus favoritos, y YouTube recomienda en base a eso. Secciones en orden fijo (el aleatorio queda opcional en Ajustes), con 'Reproducido recientemente' cronológico arriba del todo, 'Nuevos lanzamientos', 'Mix diario 1/2/3' estilo Spotify y un mix según la hora del día (mañana/tarde/noche)"),
    Feature(R.drawable.group_outlined, "Escuchar juntos", "Escucha sincronizada en tiempo real con amigos"),
    Feature(R.drawable.palette, "Temas y fondos", "Material You, modo oscuro puro AMOLED, acento dinámico y Canvas animado del artista y del álbum (a pantalla completa al girar el teléfono)"),
    Feature(R.drawable.grid_view, "Interfaz dividida (pantallas anchas)", "En tablets, Android TV y plegables desplegados la app adopta un layout dividido estilo Spotify (lista + detalle a la vez) que aprovecha todo el ancho de pantalla"),
    Feature(R.drawable.tune, "Opciones de pantalla", "Escala de densidad, alta tasa de refresco, ocultar miniatura/videos/Shorts y recortar carátula"),
    Feature(R.drawable.manage_search, "Búsqueda de ajustes", "Un buscador en Ajustes localiza cualquier opción por su nombre, sin recorrer menús"),
    Feature(R.drawable.image, "Transición de carátula (Apple Music)", "Al cambiar de canción la carátula hace un zoom y fundido dinámico estilo Apple Music (se desactiva solo en Modo Rendimiento)"),
    Feature(R.drawable.share, "Compartir y widget", "Comparte con enlaces de YouTube Music y controla la música desde el widget de vinilo"),
    Feature(R.drawable.ic_ringtone, "Establecer como tono", "Usa cualquier canción como tono de tu dispositivo, con descarga fiable (por rangos y con reanudación): usa la copia ya descargada si existe y solo baja lo que el recorte necesita"),
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
            item {
                AboutSectionCard(title = "Información legal") {
                    LegalTermsRow(onClick = { navController.navigate("settings/terms") })
                }
            }
        }
    }
}

/**
 * Ajustes ▸ Acerca de ▸ "Términos y condiciones": opens the read-only Terms screen (the same full
 * text the user accepted on first launch — clause 17.3 promises it is always available here).
 */
@Composable
private fun LegalTermsRow(onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                    painter = painterResource(R.drawable.info),
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
                text = stringResource(R.string.terms_about_entry),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.terms_about_entry_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
