package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.R

/**
 * Onboarding step 1 of migration (after genres): migrate the whole Spotify library. Spotify's OAuth
 * returns to the import screen (no app restart), so it's safe here. "Continuar" moves on to the
 * separate YouTube Music step. Fully skippable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSpotifyScreen(
    navController: NavController,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Migra tu Spotify", fontWeight = FontWeight.Bold)
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = { navController.navigate("settings/spotify_import") },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Conectar Spotify y elegir qué migrar") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    // Forward only — keep onboarding_spotify on the back stack so the user CAN go back
                    // (system back) if they want, but the flow only moves forward by tapping here.
                    onClick = { navController.navigate("onboarding_youtube") },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Siguiente: YouTube Music") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_spotify),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Trae toda tu biblioteca de Spotify",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Inicia sesión en Spotify y elige qué migrar: tus playlists, canciones que te gustan y álbumes guardados. Es opcional, puedes hacerlo ahora o más tarde desde Ajustes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
