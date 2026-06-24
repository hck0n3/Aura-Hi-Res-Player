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
 * Onboarding step 2 of migration (after Spotify): migrate YouTube Music — SEPARATE from Spotify.
 *
 * It does NOT force a login here (that's what triggered the immediate Google sign-in / restart loop).
 * "Sincronizar" just opens the YouTube Music sync-selection screen; if the user isn't signed in, that
 * screen offers sign-in there (optional, user-initiated). Fully skippable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingYouTubeScreen(
    navController: NavController,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Migra tu YouTube Music", fontWeight = FontWeight.Bold)
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = {
                        // Leaving onboarding → clear ALL its steps from the back stack.
                        navController.navigate("settings/ytm_sync") {
                            popUpTo("onboarding_artists") { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Sincronizar YouTube Music") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        navController.navigate(Screens.Home.route) {
                            popUpTo("onboarding_artists") { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Comenzar") }
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
                painter = painterResource(R.drawable.sync),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Trae tu contenido de YouTube Music",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Elige qué traer: me gusta, álbumes, artistas, suscripciones y playlists. Es opcional; " +
                    "también puedes hacerlo luego desde Ajustes ▸ Importar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
