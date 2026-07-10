package iad1tya.echo.music.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import iad1tya.echo.music.constants.CipherManualRefreshAtKey
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.cipher.RemotePlayerConfig
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Light UI-only cooldown between manual refreshes. The backend fetch is a cheap conditional GET
// (304 when unchanged), but this keeps the "Forzar actualización" button from being spammed.
private const val MANUAL_REFRESH_COOLDOWN_MS = 5L * 60L * 1000L // 5 minutes

/**
 * "Descifrado de YouTube" — a power-user screen that SURFACES the fork's existing self-healing cipher
 * backend ([RemotePlayerConfig]). It only READS state (last remote contact, applied-config count) and
 * TRIGGERS the already-existing fetch path on demand ([RemotePlayerConfig.manualRefresh]). It never
 * changes how streams are decrypted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeDecryptionSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isUpdating by remember { mutableStateOf(false) }
    var lastUpdated by remember { mutableLongStateOf(0L) }
    var appliedConfigs by remember { mutableIntStateOf(0) }

    // Read the current backend state once when the screen opens (both are cheap, in-memory reads).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            lastUpdated = RemotePlayerConfig.lastRefreshTimeMs()
            appliedConfigs = RemotePlayerConfig.knownHashCount()
        }
    }

    val (lastManualRefresh, onLastManualRefreshChange) = rememberPreference(
        CipherManualRefreshAtKey,
        defaultValue = 0L,
    )

    // Ticking clock so the cooldown countdown updates live.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastManualRefresh) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val elapsedSinceManual = now - lastManualRefresh
    // Guard clock rollback (negative elapsed): treat as expired rather than locking the button forever.
    val cooldownActive = lastManualRefresh > 0L && elapsedSinceManual in 0 until MANUAL_REFRESH_COOLDOWN_MS
    val cooldownRemaining = if (cooldownActive) MANUAL_REFRESH_COOLDOWN_MS - elapsedSinceManual else 0L
    val cooldownMinutes = cooldownRemaining / (1000L * 60L)
    val cooldownSeconds = (cooldownRemaining / 1000L) % 60L

    val lastUpdatedText = if (lastUpdated > 0L) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastUpdated))
    } else {
        stringResource(R.string.cipher_last_updated_never)
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.sync),
                    title = { Text(stringResource(R.string.force_update_cipher)) },
                    description = {
                        if (cooldownActive) {
                            Text(
                                text = stringResource(
                                    R.string.cipher_cooldown,
                                    "${cooldownMinutes}m ${cooldownSeconds}s"
                                ),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(stringResource(R.string.force_update_cipher_desc))
                        }
                    },
                    trailingContent = if (isUpdating) {
                        {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(40.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        null
                    },
                    enabled = !isUpdating && !cooldownActive,
                    onClick = {
                        if (!isUpdating && !cooldownActive) {
                            isUpdating = true
                            Toast.makeText(context, R.string.cipher_updating, Toast.LENGTH_SHORT).show()
                            scope.launch(Dispatchers.IO) {
                                val epochBefore = RemotePlayerConfig.configEpoch
                                val reached = RemotePlayerConfig.manualRefresh(context)
                                val epochAfter = RemotePlayerConfig.configEpoch
                                val newTime = RemotePlayerConfig.lastRefreshTimeMs()
                                val newCount = RemotePlayerConfig.knownHashCount()
                                withContext(Dispatchers.Main) {
                                    isUpdating = false
                                    if (newTime > 0L) lastUpdated = newTime
                                    appliedConfigs = newCount
                                    // Only start the cooldown when the server was actually reached, so an
                                    // offline attempt does not lock the user out until it can even succeed.
                                    if (reached) onLastManualRefreshChange(System.currentTimeMillis())
                                    when {
                                        reached && epochAfter != epochBefore ->
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.cipher_update_success, newCount),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        reached ->
                                            Toast.makeText(context, R.string.cipher_update_unchanged, Toast.LENGTH_LONG).show()
                                        else ->
                                            Toast.makeText(context, R.string.cipher_update_failed, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.cipher_applied_configs)) },
                    description = {
                        Text(
                            text = stringResource(R.string.cipher_applied_configs_count, appliedConfigs),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.history),
                    title = { Text(stringResource(R.string.cipher_last_updated)) },
                    description = {
                        Text(
                            text = lastUpdatedText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = stringResource(R.string.youtube_decryption_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.youtube_decryption_settings)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}
