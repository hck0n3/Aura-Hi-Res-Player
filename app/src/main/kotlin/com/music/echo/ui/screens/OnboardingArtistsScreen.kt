package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.ui.theme.BrandAccent
import iad1tya.echo.music.viewmodels.OnboardingViewModel
import kotlinx.coroutines.launch

private const val MIN_ARTISTS = 3

@OptIn(ExperimentalMaterial3Api::class)
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
                        "Elige al menos $MIN_ARTISTS para empezar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Seleccionados: ${selected.size}" + if (selected.size < MIN_ARTISTS) " (faltan ${MIN_ARTISTS - selected.size})" else "",
                    style = MaterialTheme.typography.bodyMedium,
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
                    modifier = Modifier.fillMaxWidth().height(50.dp),
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (viewModel.searching) {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { artist ->
                    val isSelected = artist.id in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggle(artist) }
                            .padding(vertical = 8.dp),
                    ) {
                        AsyncImage(
                            model = artist.thumbnail,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            artist.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(BrandAccent),
                                Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
