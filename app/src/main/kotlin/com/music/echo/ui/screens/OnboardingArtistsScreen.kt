package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.theme.BrandAccent
import iad1tya.echo.music.viewmodels.OnboardingViewModel
import kotlinx.coroutines.launch

private const val MIN_ARTISTS = 3
private val SEARCH_SUGGESTIONS = listOf(
    "Pop", "Rock", "Reggaetón", "Hip-Hop", "Electrónica", "Latina", "K-Pop", "Cristiana",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingArtistsScreen(
    navController: NavController,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Elige tus artistas favoritos", fontWeight = FontWeight.Bold)
                    Text(
                        "Mínimo $MIN_ARTISTS — así armamos tu inicio a tu gusto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = if (selected.size < MIN_ARTISTS)
                        "Seleccionados: ${selected.size} (faltan ${MIN_ARTISTS - selected.size})"
                    else "Seleccionados: ${selected.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected.size >= MIN_ARTISTS) BrandAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        finishing = true
                        scope.launch {
                            viewModel.finish()
                            finishing = false
                            navController.navigate("onboarding_genres") {
                                popUpTo("onboarding_artists") { inclusive = true }
                            }
                        }
                    },
                    enabled = selected.size >= MIN_ARTISTS && !finishing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (finishing) "Guardando…" else "Finalizar")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                label = { Text("Buscar artista") },
                leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            when {
                viewModel.searching -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                query.isBlank() -> {
                    // Friendly, interactive empty state: quick-search chips so it isn't a blank screen.
                    Text(
                        "Empieza a buscar o prueba con:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SEARCH_SUGGESTIONS.forEach { term ->
                            AssistChip(
                                onClick = { viewModel.query.value = term },
                                label = { Text(term) },
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results, key = { it.id }) { artist ->
                            val isSelected = artist.id in selected
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggle(artist) },
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    AsyncImage(
                                        model = artist.thumbnail,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .then(
                                                if (isSelected) Modifier.border(3.dp, BrandAccent, CircleShape)
                                                else Modifier
                                            ),
                                    )
                                    if (isSelected) {
                                        Box(
                                            Modifier.size(26.dp).clip(CircleShape).background(BrandAccent),
                                            Alignment.Center,
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.check),
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    artist.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
