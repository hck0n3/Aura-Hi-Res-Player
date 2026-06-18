package iad1tya.echo.music.ui.screens.equalizer.autoeq

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.launch
import android.widget.Toast

/**
 * Auto-EQ: search the AutoEq (jaakkopasanen) catalog for your headphone model and apply its profile
 * to the equalizer. The catalog is cached on disk (24 h) — use "Actualizar base de datos" to refresh.
 */
@Composable
fun AutoEqScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AutoEqRepository(context) }
    val eqViewModel: AxionEqViewModel = hiltViewModel()
    val eqEnabled by eqViewModel.enabled.collectAsState()

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<AutoEqEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var applyingName by remember { mutableStateOf<String?>(null) }

    suspend fun load(forceRefresh: Boolean) {
        if (forceRefresh) refreshing = true else loading = true
        entries = repo.getIndex(forceRefresh = forceRefresh)
        loading = false
        refreshing = false
    }

    LaunchedEffect(Unit) { load(forceRefresh = false) }

    val filtered = remember(entries, query) {
        if (query.isBlank()) entries.take(100)
        else entries.filter { it.name.contains(query, ignoreCase = true) }.take(200)
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
            "Busca tu modelo y aplica su perfil AutoEq al ecualizador. El catálogo (más de 5000 " +
                "auriculares) se descarga de internet la primera vez y luego queda guardado para cargar al instante.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Enable / disable Auto-EQ (mirrors the equalizer on/off).
        ListItem(
            headlineContent = { Text("Auto-EQ activado", fontWeight = FontWeight.Medium) },
            supportingContent = { Text(if (eqEnabled) "El ecualizador está aplicando la curva" else "Desactivado") },
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
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.name + "|" + it.source }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.name, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(
                                if (applyingName == entry.name) "Aplicando…" else entry.source,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        modifier = Modifier.clickable(enabled = eqEnabled) {
                            applyingName = entry.name
                            scope.launch {
                                val profile = repo.fetchProfile(entry)
                                if (profile == null) {
                                    Toast.makeText(context, "No se pudo descargar el perfil", Toast.LENGTH_SHORT).show()
                                } else {
                                    val gains = projectAutoEqToBands(profile, EqConstants.FREQUENCIES)
                                    eqViewModel.setBandsGains(gains, fromUser = true)
                                    eqViewModel.setPreamp(
                                        profile.preampDb.toFloat()
                                            .coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
                                    )
                                    eqViewModel.setEnabled(true)
                                    Toast.makeText(context, "Auto-EQ aplicado: ${entry.name}", Toast.LENGTH_SHORT).show()
                                }
                                applyingName = null
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
