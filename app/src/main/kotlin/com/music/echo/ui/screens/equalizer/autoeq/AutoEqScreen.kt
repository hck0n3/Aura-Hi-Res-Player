package iad1tya.echo.music.ui.screens.equalizer.autoeq

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.eq.autoeq.AutoEqEntry
import iad1tya.echo.music.eq.autoeq.AutoEqRepository
import iad1tya.echo.music.eq.autoeq.projectAutoEqToBands
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.ui.screens.equalizer.axion.AxionEqViewModel
import kotlinx.coroutines.launch
import android.widget.Toast

/**
 * Auto-EQ: search the AutoEq (jaakkopasanen) catalog for your headphone model and apply its profile
 * to the equalizer. Online to fetch; the applied curve is then stored and works offline.
 */
@Composable
fun AutoEqScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AutoEqRepository(context) }
    val eqViewModel: AxionEqViewModel = hiltViewModel()

    var loading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<AutoEqEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var applyingName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        entries = repo.getIndex()
        loading = false
    }

    val filtered = remember(entries, query) {
        if (query.isBlank()) entries.take(100)
        else entries.filter { it.name.contains(query, ignoreCase = true) }.take(200)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Busca tu auricular (ej. WH-1000XM5)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No se pudo cargar el catálogo AutoEq. Revisa tu conexión.")
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
                        modifier = Modifier.clickable {
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
