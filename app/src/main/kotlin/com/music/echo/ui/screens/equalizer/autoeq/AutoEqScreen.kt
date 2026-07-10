package iad1tya.echo.music.ui.screens.equalizer.autoeq

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.R
import iad1tya.echo.music.eq.autoeq.AutoEqEntry
import iad1tya.echo.music.eq.autoeq.AutoEqRepository
import iad1tya.echo.music.eq.autoeq.projectAutoEqToBands
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.ui.screens.equalizer.axion.AxionEqViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.widget.Toast

/**
 * Auto-EQ: search the AutoEq (jaakkopasanen) catalog for your headphone model and apply its profile
 * to the equalizer. A bundled snapshot renders instantly; a downloaded copy is cached on disk for
 * 30 days and refreshed in the background — "Actualizar base de datos" forces a refresh.
 */
@OptIn(FlowPreview::class)
@Composable
fun AutoEqScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AutoEqRepository(context) }
    val eqViewModel: AxionEqViewModel = hiltViewModel()
    val eqEnabled by eqViewModel.enabled.collectAsState()
    val autoEqActive by eqViewModel.autoEqActive.collectAsState()

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<AutoEqEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var applyingName by remember { mutableStateOf<String?>(null) }

    // FAVORITES: persisted in the same SharedPreferences file the EQ ViewModel uses. A favorite id is
    // entry.name + "|" + entry.source (the repo identity scheme). We COPY on read and store a FRESH set
    // instance on write, because SharedPreferences returns a shared (mutable) set you must not edit.
    val favPrefs = remember { context.getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(favPrefs.getStringSet("autoeq_favorites", emptySet())!!.toSet()) }
    fun favId(e: AutoEqEntry) = e.name + "|" + e.source
    fun toggleFav(e: AutoEqEntry) {
        val id = favId(e)
        val next = favorites.toMutableSet().apply { if (!add(id)) remove(id) }
        favorites = next
        favPrefs.edit().putStringSet("autoeq_favorites", HashSet(next)).apply()
    }

    suspend fun load(forceRefresh: Boolean) {
        if (forceRefresh) refreshing = true else loading = true
        entries = repo.getIndex(forceRefresh = forceRefresh)
        loading = false
        refreshing = false
    }

    LaunchedEffect(Unit) {
        // First render is INSTANT: fresh disk cache or the bundled asset, no network.
        load(forceRefresh = false)
        // Network refresh runs only in the BACKGROUND (one-shot, never blocks the list): if the disk
        // cache is missing/stale, download the current catalog and swap it in when it lands.
        val fresh = withContext(Dispatchers.IO) { repo.isIndexFresh() }
        if (!fresh) {
            val updated = repo.getIndex(forceRefresh = true)
            if (updated.isNotEmpty()) entries = updated
        }
    }

    // SEARCH: debounced ~200 ms and filtered on Dispatchers.Default with a pre-lowercased name per
    // entry — filtering 5000+ entries on the main thread per keystroke made typing stutter. The
    // lowercased list is rebuilt (off the main thread) only when the catalog itself changes.
    var filtered by remember { mutableStateOf<List<AutoEqEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        var lowerSource: List<AutoEqEntry> = emptyList()
        var lowered: List<Pair<AutoEqEntry, String>> = emptyList()
        snapshotFlow { entries to query }
            .debounce { (_, q) -> if (q.isBlank()) 0L else 200L }
            .collectLatest { (list, q) ->
                filtered = withContext(Dispatchers.Default) {
                    if (q.isBlank()) {
                        list.take(100)
                    } else {
                        if (lowerSource !== list) {
                            lowered = list.map { it to it.name.lowercase() }
                            lowerSource = list
                        }
                        val needle = q.lowercase()
                        lowered.asSequence()
                            .filter { it.second.contains(needle) }
                            .map { it.first }
                            .take(200)
                            .toList()
                    }
                }
            }
    }

    // Favorites come from the FULL catalog (not the filtered search), sorted by name.
    val favEntries = remember(entries, favorites) {
        entries.filter { favId(it) in favorites }.sortedBy { it.name.lowercase() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp),
    ) {
        // Header (keeps the search bar off the very top).
        Text(
            "Auto-EQ por auricular",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Text(
            "Busca tu modelo y aplica su perfil AutoEq al ecualizador. El catálogo (más de 8000 " +
                "auriculares) viene incluido en la app y carga al instante; se actualiza solo en segundo plano.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Enable / disable Auto-EQ (mirrors the equalizer on/off).
        ListItem(
            headlineContent = { Text("Auto-EQ activado", fontWeight = FontWeight.Medium) },
            supportingContent = {
                Text(
                    when {
                        !eqEnabled -> "Desactivado"
                        autoEqActive -> "Perfil Auto-EQ aplicado (se suma a tu EQ, no lo reemplaza)"
                        else -> "El ecualizador está aplicando la curva"
                    },
                )
            },
            trailingContent = {
                Switch(checked = eqEnabled, onCheckedChange = { eqViewModel.setEnabled(it) })
            },
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (entries.isEmpty()) "Catálogo no cargado" else "Catálogo: ${entries.size} modelos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                enabled = !refreshing && !loading,
                onClick = { scope.launch { load(forceRefresh = true) } },
            ) {
                Text(if (refreshing) "Actualizando…" else "Actualizar base de datos")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar (ej. WH-1000XM5)") },
            singleLine = true,
            enabled = eqEnabled,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No se pudo cargar el catálogo AutoEq. Pulsa 'Actualizar base de datos'.")
            }
            else -> {
                // One result row, reused by both the "Favoritos" and "Todos" sections. The star toggle is
                // NOT gated by eqEnabled (favorites can be curated while EQ is off); only the apply-on-click
                // stays gated.
                @Composable
                fun EqRow(entry: AutoEqEntry) {
                    val isFav = favId(entry) in favorites
                    ListItem(
                        headlineContent = { Text(entry.name, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(
                                if (applyingName == entry.name) "Aplicando…" else entry.source,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { toggleFav(entry) }) {
                                Icon(
                                    imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (isFav) "Quitar de favoritos" else "Añadir a favoritos",
                                    tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.clickable(enabled = eqEnabled) {
                            applyingName = entry.name
                            scope.launch {
                                val profile = repo.fetchProfile(entry)
                                if (profile == null) {
                                    Toast.makeText(context, "No se pudo descargar el perfil", Toast.LENGTH_SHORT).show()
                                } else {
                                    val gains = projectAutoEqToBands(profile, EqConstants.FREQUENCIES)
                                    // Apply bands + preamp + enable in ONE shot (avoids the audio
                                    // stutter from three back-to-back DSP re-applies + DB writes).
                                    eqViewModel.applyProfileBatch(
                                        gains,
                                        profile.preampDb.toFloat(),
                                        isAutoEq = true,
                                    )
                                    Toast.makeText(context, "Auto-EQ aplicado: ${entry.name}", Toast.LENGTH_SHORT).show()
                                }
                                applyingName = null
                            }
                        },
                    )
                    HorizontalDivider()
                }

                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    // "Favoritos" section only when there are favorites AND no active search (the search
                    // shows the flat filtered list). Keys are prefixed so a model that's both favorited
                    // and in the "Todos" list doesn't collide on the LazyColumn key.
                    if (favEntries.isNotEmpty() && query.isBlank()) {
                        item(key = "header_fav") {
                            Text(
                                "Favoritos",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(favEntries, key = { "fav_" + favId(it) }) { entry -> EqRow(entry) }
                        item(key = "header_all") {
                            Text(
                                "Todos",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(filtered, key = { "all_" + favId(it) }) { entry -> EqRow(entry) }
                }
            }
        }
    }
}
