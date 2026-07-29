@file:OptIn(ExperimentalMaterial3Api::class)

package iad1tya.echo.music.ui.screens.migration

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.TidalClientIdKey
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.rememberPreference

/**
 * Tidal migration setup — HONEST by design.
 *
 * Tidal's Open API needs a client id (PKCE, no secret). This project is open source, so no id can be
 * committed; the owner pastes their own from developer.tidal.com and it is stored via [TidalClientIdKey]
 * (kept off any committed source). The OAuth login/token-exchange flow itself is being finalised in the
 * migration backend (encrypted token vault + redirect callback), so rather than a placebo "connect"
 * button that does nothing, this screen tells the user plainly that the sign-in step is coming — the
 * client-id setup is real, and the file route below always works right now.
 */
@Composable
fun MigrationTidalScreen(navController: NavController) {
    val context = LocalContext.current
    val (savedClientId, setSavedClientId) = rememberPreference(TidalClientIdKey, "")
    var clientIdField by remember(savedClientId) { mutableStateOf(savedClientId) }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.migrate_source_tidal)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .verticalScroll(rememberScrollState())
                .padding(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 32.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.migrate_tidal_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Client id entry
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.migrate_tidal_clientid_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.migrate_tidal_clientid_how),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = clientIdField,
                        onValueChange = { clientIdField = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.migrate_tidal_clientid_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { openUrl("https://developer.tidal.com/dashboard") },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.migrate_tidal_open_dashboard)) }
                        Button(
                            onClick = { setSavedClientId(clientIdField.trim()) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.migrate_tidal_save)) }
                    }
                    if (savedClientId.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.migrate_tidal_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Honest "coming soon" state for the login flow itself.
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painter = painterResource(R.drawable.sync), contentDescription = null)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.migrate_tidal_soon_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.migrate_tidal_soon_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Meanwhile, the file route always works.
            Text(
                text = stringResource(R.string.migrate_tidal_use_file),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
