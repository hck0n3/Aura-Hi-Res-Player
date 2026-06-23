package iad1tya.echo.music.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.AccountSettingsViewModel

/**
 * Manual "sync from YouTube Music" hub. Reached from Settings ▸ Import (next to Spotify import) and
 * from first-run onboarding. The user triggers each kind of content on demand — nothing runs
 * automatically. Requires being signed in to YouTube Music; otherwise it offers a sign-in button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtmSyncScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AccountSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sincronizar desde YouTube Music") },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (!isLoggedIn) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Inicia sesión en YouTube Music para sincronizar tu contenido (me gusta, álbumes, artistas, suscripciones y playlists).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate("login") },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(stringResource(R.string.login)) }
                return@Column
            }

            Text(
                "Elige qué traer de tu cuenta. Nada se sincroniza solo: tú decides qué y cuándo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.sync),
                        title = { Text("Sincronizar todo") },
                        description = { Text("Me gusta, álbumes, artistas, suscripciones, playlists y biblioteca") },
                        onClick = { viewModel.syncAll(); toast("Sincronizando todo…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.favorite),
                        title = { Text("Me gusta (canciones)") },
                        onClick = { viewModel.syncLikedSongs(); toast("Sincronizando me gusta…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.favorite_border),
                        title = { Text("Álbumes favoritos") },
                        onClick = { viewModel.syncLikedAlbums(); toast("Sincronizando álbumes…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.add_circle),
                        title = { Text("Artistas y suscripciones") },
                        onClick = { viewModel.syncArtists(); toast("Sincronizando artistas…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.playlist_add),
                        title = { Text("Playlists guardadas") },
                        onClick = { viewModel.syncPlaylists(); toast("Sincronizando playlists…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.cached),
                        title = { Text("Biblioteca (canciones)") },
                        onClick = { viewModel.syncLibrarySongs(); toast("Sincronizando biblioteca…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.backup),
                        title = { Text("Subidas (canciones y álbumes)") },
                        onClick = { viewModel.syncUploads(); toast("Sincronizando subidas…") },
                    ),
                ),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
