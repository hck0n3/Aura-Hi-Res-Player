package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.screens.Screens

/**
 * Optional onboarding step (after genres, before the Google sign-in prompt): tells the user they
 * can migrate their whole Spotify library and lets them open the Spotify import screen to sign in
 * to Spotify and choose what to bring over. Fully skippable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSpotifyScreen(
    navController: NavController,
) {
    var showLoginPrompt by remember { mutableStateOf(false) }

    fun goHome() {
        navController.navigate(Screens.Home.route) {
            popUpTo("onboarding_spotify") { inclusive = true }
        }
    }

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
                    onClick = { showLoginPrompt = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Continuar") }
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
            Spacer(Modifier.height(16.dp))
            Text(
                "¿Usas YouTube Music? Al iniciar sesión en Google podrás sincronizar tu contenido (me gusta, álbumes, artistas, suscripciones y playlists) cuando quieras desde Ajustes ▸ Cuenta ▸ Sincronización con YouTube Music.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showLoginPrompt) {
        AlertDialog(
            onDismissRequest = { showLoginPrompt = false; goHome() },
            title = { Text("Inicia sesión en Google") },
            text = { Text("Para sincronizar tu cuenta y mejorar las recomendaciones. Tus gustos elegidos se conservan inicies sesión o no.") },
            confirmButton = {
                TextButton(onClick = {
                    showLoginPrompt = false
                    navController.navigate("login")
                }) { Text("Iniciar sesión") }
            },
            dismissButton = {
                TextButton(onClick = { showLoginPrompt = false; goHome() }) { Text("Ahora no") }
            },
        )
    }
}
