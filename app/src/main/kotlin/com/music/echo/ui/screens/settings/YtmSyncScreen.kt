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
import iad1tya.echo.music.constants.YtmAutoSyncFreqDaysKey
import iad1tya.echo.music.constants.YtmLastSyncKey
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
    onboarding: Boolean = false,
) {
    val context = LocalContext.current
    val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }
    // Login cold-restarts the app; this flag brings the user back here (selection) after the restart.
    val (_, setOpenAfterLogin) = rememberPreference(iad1tya.echo.music.constants.OpenYtmSyncAfterLoginKey, false)

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
        bottomBar = {
            // During onboarding, finish from here without going back — the sync keeps running in the
            // background (WorkManager), so the user can move on to the app immediately.
            if (onboarding) {
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    androidx.compose.material3.Button(
                        onClick = {
                            navController.navigate("home") {
                                popUpTo("onboarding_artists") { inclusive = true }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Comenzar a usar Aura") }
                }
            }
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
                    onClick = {
                        setOpenAfterLogin(true)
                        navController.navigate("login")
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(stringResource(R.string.login)) }
                return@Column
            }

            Text(
                "Elige qué traer de tu cuenta. La sincronización corre en segundo plano y continúa aunque " +
                    "cierres la app, hasta completarse. Tú decides qué y cuándo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            val W = iad1tya.echo.music.utils.YtmSyncWorker
            fun start(type: String, msg: String) {
                W.enqueue(context, type)
                toast(msg)
            }

            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.sync),
                        title = { Text("Sincronizar todo") },
                        description = { Text("Me gusta, álbumes, artistas, suscripciones, playlists y biblioteca") },
                        onClick = { start(W.TYPE_ALL, "Sincronizando todo… (continúa en segundo plano)") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.favorite),
                        title = { Text("Me gusta (canciones)") },
                        onClick = { start(W.TYPE_LIKED_SONGS, "Sincronizando me gusta…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.favorite_border),
                        title = { Text("Álbumes favoritos") },
                        onClick = { start(W.TYPE_LIKED_ALBUMS, "Sincronizando álbumes…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.add_circle),
                        title = { Text("Artistas y suscripciones") },
                        onClick = { start(W.TYPE_ARTISTS, "Sincronizando artistas…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.playlist_add),
                        title = { Text("Playlists guardadas") },
                        onClick = { start(W.TYPE_PLAYLISTS, "Sincronizando playlists…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.cached),
                        title = { Text("Biblioteca (canciones)") },
                        onClick = { start(W.TYPE_LIBRARY, "Sincronizando biblioteca…") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.backup),
                        title = { Text("Subidas (canciones y álbumes)") },
                        onClick = { start(W.TYPE_UPLOADS, "Sincronizando subidas…") },
                    ),
                ),
            )

            Spacer(Modifier.height(20.dp))
            Text(
                "Sincronización automática",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )

            val (autoFreq, setAutoFreq) = rememberPreference(YtmAutoSyncFreqDaysKey, 0)
            val (lastSyncMs, _) = rememberPreference(YtmLastSyncKey, 0L)
            // Tick once a minute so the "hace X" elapsed time stays current while the screen is open.
            val nowTick by androidx.compose.runtime.produceState(initialValue = System.currentTimeMillis()) {
                while (true) {
                    kotlinx.coroutines.delay(60_000)
                    value = System.currentTimeMillis()
                }
            }

            fun applyFreq(days: Int) {
                setAutoFreq(days)
                iad1tya.echo.music.utils.YtmAutoSyncWorker.schedule(context, days)
                toast(
                    when {
                        days <= 0 -> "Sincronización automática desactivada"
                        days == 1 -> "Se sincronizará cada día"
                        else -> "Se sincronizará cada $days días"
                    },
                )
            }

            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.sync),
                        title = { Text("Desactivada") },
                        description = { if (autoFreq <= 0) Text("Seleccionada") },
                        onClick = { applyFreq(0) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.sync),
                        title = { Text("Cada día") },
                        description = { if (autoFreq == 1) Text("Seleccionada") },
                        onClick = { applyFreq(1) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.sync),
                        title = { Text("Cada semana") },
                        description = { if (autoFreq == 7) Text("Seleccionada") },
                        onClick = { applyFreq(7) },
                    ),
                ),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Última sincronización: " + when {
                    lastSyncMs <= 0L -> "nunca"
                    else -> {
                        val diff = (nowTick - lastSyncMs).coerceAtLeast(0L)
                        when {
                            diff < 60_000L -> "hace un momento"
                            diff < 3_600_000L -> "hace ${diff / 60_000L} min"
                            diff < 86_400_000L -> "hace ${diff / 3_600_000L} h"
                            else -> "hace ${diff / 86_400_000L} días"
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
