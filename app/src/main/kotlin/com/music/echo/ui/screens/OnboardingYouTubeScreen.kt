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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.OpenYtmSyncAfterLoginKey
import iad1tya.echo.music.utils.rememberPreference

/**
 * Onboarding step 2 of migration (after Spotify): migrate YouTube Music — SEPARATE from Spotify.
 *
 * YouTube login cold-restarts the app (unlike Spotify's OAuth), so we can't simply "return" in-place.
 * Instead, tapping connect sets [OpenYtmSyncAfterLoginKey] and opens the login; after the restart
 * MainActivity reads that flag once and lands the user on the sync-selection screen. If already signed
 * in, we go straight to the selection. Fully skippable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingYouTubeScreen(
    navController: NavController,
) {
    val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }
    val (_, setOpenAfterLogin) = rememberPreference(OpenYtmSyncAfterLoginKey, false)

    fun goHome() {
        // Clear any pending "return to sync after login" flag in case the user tapped connect, backed
        // out of the login without finishing (no restart), and then skipped — avoids a stray open later.
        setOpenAfterLogin(false)
        navController.navigate(Screens.Home.route) {
            popUpTo("onboarding_youtube") { inclusive = true }
        }
    }

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
                        if (isLoggedIn) {
                            // Already signed in → go straight to choosing what to sync.
                            navController.navigate("settings/ytm_sync") {
                                popUpTo("onboarding_youtube") { inclusive = true }
                            }
                        } else {
                            // Sign in (cold-restarts); MainActivity then returns to the sync selection.
                            setOpenAfterLogin(true)
                            navController.navigate("login")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(if (isLoggedIn) "Elegir qué sincronizar" else "Conectar YouTube Music") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { goHome() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Omitir") }
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
                "Inicia sesión y elige qué traer: me gusta, álbumes, artistas, suscripciones y playlists. " +
                    "Es opcional; también puedes hacerlo luego desde Ajustes ▸ Importar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
