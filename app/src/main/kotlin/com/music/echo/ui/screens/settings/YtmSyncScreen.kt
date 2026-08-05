package iad1tya.echo.music.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import iad1tya.echo.music.constants.YtmUploadSyncKey
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.utils.UploadCategoryProgress
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
                    IconButton(onClick = navController::navigateUp, onLongClick = null) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
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
                        // Playlists are de-starved by running them EARLY in the full sync (SyncUtils) + the
                        // album step no longer re-fetches every album. We deliberately do NOT also enqueue a
                        // separate TYPE_PLAYLISTS worker here: insert(PlaylistEntity) is not a browseId-upsert
                        // (random PK), so two playlist syncs racing outside syncChannel could create duplicate
                        // rows. One ordered full sync is enough and safe.
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
                        description = { Text("Incluye tus me gusta (favoritos)") },
                        onClick = {
                            // "Biblioteca" (FEmusic_liked_videos) sólo marca inLibrary, nunca liked; los
                            // favoritos viven en LM. Encolamos ambos para que migrar la biblioteca traiga
                            // también los me gusta. Son trabajos únicos distintos y additivos (upsert),
                            // así que no hay doble ejecución ni bucle.
                            W.enqueue(context, W.TYPE_LIKED_SONGS)
                            start(W.TYPE_LIBRARY, "Sincronizando biblioteca y me gusta…")
                        },
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

            LibraryUploadSection(
                viewModel = viewModel,
                isLoggedIn = isLoggedIn,
                onToast = ::toast,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * "Copia de seguridad en YouTube Music" — the upward half of the sync, and the answer to
 * *"que informe cuáles ya están sincronizadas y cuáles se están sincronizando, y que también informe
 * cuando termine de sincronizar toda la biblioteca"*.
 *
 * Every number here comes from [iad1tya.echo.music.utils.LibraryUploadSync]'s StateFlow, which is
 * recomputed from the database and persisted to DataStore — so the report is accurate after a cold
 * start, not just within the session that ran the sync.
 */
@Composable
private fun LibraryUploadSection(
    viewModel: AccountSettingsViewModel,
    isLoggedIn: Boolean,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    // FALSE fallback on purpose — the stored value is always explicit (see YtmUploadOptInV1AppliedKey).
    // The switch shown here is the opt-in for anyone the one-time migration defaulted to off.
    val (uploadEnabled, setUploadEnabled) = rememberPreference(YtmUploadSyncKey, false)
    val progress by viewModel.uploadProgress.collectAsState()

    // Network-free: reads the persisted snapshot and recounts from the DB. Never uploads anything.
    LaunchedEffect(Unit) { viewModel.refreshUploadProgress() }

    Text(
        text = stringResource(R.string.ytm_upload_section_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )

    Material3SettingsGroup(
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.backup),
                title = { Text(stringResource(R.string.ytm_upload_toggle_title)) },
                description = {
                    Text(
                        if (uploadEnabled) stringResource(R.string.ytm_upload_toggle_desc)
                        else stringResource(R.string.ytm_upload_disabled_hint),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = uploadEnabled,
                        onCheckedChange = { setUploadEnabled(it) },
                    )
                },
                onClick = { setUploadEnabled(!uploadEnabled) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.sync),
                title = { Text(stringResource(R.string.ytm_upload_run_now)) },
                description = { Text(stringResource(R.string.ytm_upload_run_now_desc)) },
                onClick = {
                    when {
                        !isLoggedIn -> onToast(context.getString(R.string.ytm_upload_needs_login))
                        !uploadEnabled -> onToast(context.getString(R.string.ytm_upload_disabled_hint))
                        else -> {
                            // Explicit request only — an upload NEVER starts by itself on app launch.
                            iad1tya.echo.music.utils.YtmSyncWorker.enqueue(
                                context,
                                iad1tya.echo.music.utils.YtmSyncWorker.TYPE_UPLOAD_LIBRARY,
                            )
                            onToast(context.getString(R.string.ytm_upload_started))
                        }
                    }
                },
            ),
        ),
    )

    Spacer(Modifier.height(12.dp))

    UploadCategoryRow(stringResource(R.string.ytm_upload_cat_playlists), progress.playlists)
    UploadCategoryRow(stringResource(R.string.ytm_upload_cat_artists), progress.artists)
    UploadCategoryRow(stringResource(R.string.ytm_upload_cat_liked_songs), progress.likedSongs)
    UploadCategoryRow(stringResource(R.string.ytm_upload_cat_liked_albums), progress.likedAlbums)

    Spacer(Modifier.height(12.dp))

    // The completion notice he asked for explicitly, and its honest counterpart while work remains.
    // Nothing is claimed until the counts have actually been computed (`counted`) — an all-zero
    // default would otherwise flash "todo sincronizado" before a single row had been read.
    if (!progress.counted) {
        Text(
            text = stringResource(R.string.ytm_upload_in_progress),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    } else if (progress.everythingSynced) {
        Text(
            text = stringResource(R.string.ytm_upload_all_done),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
        Text(
            text = stringResource(R.string.ytm_upload_all_done_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 2.dp),
        )
    } else {
        Text(
            text = stringResource(R.string.ytm_upload_pending_summary, progress.totalPending),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
        Text(
            text = stringResource(R.string.ytm_upload_continues_background),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 2.dp),
        )
    }

    // Why a pass stopped early (offline, signed out, switched off) — never a silent no-op.
    progress.stoppedReason?.let { reason ->
        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp),
        )
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = if (progress.lastCompletedEpochMs > 0L) {
            stringResource(
                R.string.ytm_upload_last_completed,
                android.text.format.DateUtils.getRelativeTimeSpanString(
                    progress.lastCompletedEpochMs,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
            )
        } else {
            stringResource(R.string.ytm_upload_never_completed)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp),
    )
    if (progress.requestsLastRun > 0) {
        Text(
            text = stringResource(R.string.ytm_upload_requests_last_run, progress.requestsLastRun),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 2.dp),
        )
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.ytm_upload_only_followed_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp),
    )
}

/** One "X de Y sincronizadas / faltan N / Sincronizando…" line, plus a progress bar. */
@Composable
private fun UploadCategoryRow(label: String, state: UploadCategoryProgress) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = when {
                    state.running -> stringResource(R.string.ytm_upload_in_progress)
                    state.total == 0 -> stringResource(R.string.ytm_upload_nothing_to_sync)
                    state.isComplete -> stringResource(R.string.ytm_upload_category_done)
                    else -> stringResource(R.string.ytm_upload_count_pending, state.pending)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isComplete) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.total > 0) {
            Text(
                text = stringResource(R.string.ytm_upload_count_synced, state.synced, state.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { state.synced.toFloat() / state.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
