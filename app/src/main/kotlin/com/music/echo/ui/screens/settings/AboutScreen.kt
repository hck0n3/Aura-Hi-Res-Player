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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.utils.backToMain

private data class Feature(val icon: Int, val title: String, val subtitle: String)

private val PLAYBACK_FEATURES = listOf(
    Feature(R.drawable.play, "Reproducción", "Sin cortes (gapless), crossfade, normalización y temporizador de apagado"),
    Feature(R.drawable.graphic_eq, "Sonido y EQ", "Ecualizador de 24 bandas, Auto-EQ por modelo de auricular, sonoridad tipo TIDAL con limitador true-peak, firma Aura (cuerpo + aire), realce de graves, excitador, compresor multibanda, ancho estéreo, diálogos y sala virtual HRTF"),
    Feature(R.drawable.auto_awesome, "Mejorar calidad baja", "Reduce la distorsión (declip) y regenera agudos en fuentes de bajo bitrate"),
    Feature(R.drawable.lyrics, "Letras", "Sincronizadas, con traducción por IA y desenfoque estilo Apple Music"),
    Feature(R.drawable.queue_music, "Cola", "Cola inteligente y gestión de 'a continuación'"),
)

private val LIBRARY_FEATURES = listOf(
    Feature(R.drawable.library_music, "Biblioteca y sincronización", "Biblioteca, listas y 'me gusta' de YouTube Music sincronizados"),
    Feature(R.drawable.auto_awesome, "Listas con IA", "Crea playlists describiéndolas con una frase"),
    Feature(R.drawable.download, "Importar", "Importa listas de YouTube, Spotify y Aura Hi-Res Player (.jrpl.json)"),
    Feature(R.drawable.music_history, "Release Radar e historial", "Nuevos lanzamientos de tus artistas, historial y estadísticas de escucha"),
)

private val EXTRAS_FEATURES = listOf(
    Feature(R.drawable.group_outlined, "Escuchar juntos", "Escucha sincronizada en tiempo real con amigos"),
    Feature(R.drawable.palette, "Tema Aura Glass", "Vidrio oscuro con fondo de carátula difuminada, Canvas animado y Material You"),
    Feature(R.drawable.play, "Android TV", "Se instala y navega con control en televisores"),
    Feature(R.drawable.download, "Auto-actualización", "Aura Hi-Res Update: descarga e instala nuevas versiones sin desinstalar"),
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
                AboutSectionCard(title = "Playback & Audio") {
                    FeatureList(PLAYBACK_FEATURES)
                }
            }
            item {
                AboutSectionCard(title = "Library & Content") {
                    FeatureList(LIBRARY_FEATURES)
                }
            }
            item {
                AboutSectionCard(title = "Social & Personalization") {
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
            Image(
                painter = painterResource(R.drawable.jr_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Aura Hi-Res Player",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
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
