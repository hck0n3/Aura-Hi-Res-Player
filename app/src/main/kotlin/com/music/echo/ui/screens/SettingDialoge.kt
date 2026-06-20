package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AccountEmailKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.UseLoginForBrowse
import iad1tya.echo.music.constants.YtmSyncKey
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.HomeViewModel
import androidx.compose.ui.layout.ContentScale

@Composable
fun SettingDialoge(
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    homeViewModel: HomeViewModel
) {
    val uriHandler = LocalUriHandler.current
    val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        innerTubeCookie.isNotEmpty() && "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val (accountEmail, _) = rememberPreference(AccountEmailKey, "")
    val accountName by homeViewModel.accountName.collectAsState()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, false)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val primaryColor = MaterialTheme.colorScheme.onSurface
        val onSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Spacer(modifier = Modifier.size(24.dp))
                    
                    Text(
                        text = "Aura Hi-Res Player",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = primaryColor,
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.close),
                            contentDescription = "Close",
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // When signed out, show a clear "Iniciar sesión" entry here (the avatar sheet is the
                // main account entry point) so users can always find how to log in.
                if (!isLoggedIn) {
                    Material3SettingsGroup(
                        title = "Cuenta",
                        compact = true,
                        items = listOf(
                            Material3SettingsItem(
                                title = { Text("Iniciar sesión") },
                                icon = painterResource(R.drawable.login),
                                onClick = { onNavigate("login") }
                            )
                        )
                    )
                }

                if (isLoggedIn) {
                    Material3SettingsGroup(
                        title = "Preferences",
                        compact = true,
                        items = listOf(
                            Material3SettingsItem(
                                title = { Text("Usar la cuenta para explorar") },
                                icon = painterResource(R.drawable.add_circle),
                                trailingContent = {
                                    Switch(
                                        checked = useLoginForBrowse,
                                        onCheckedChange = {
                                            com.music.innertube.YouTube.useLoginForBrowse = it
                                            onUseLoginForBrowseChange(it)
                                        },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                },
                                onClick = {
                                    val newVal = !useLoginForBrowse
                                    com.music.innertube.YouTube.useLoginForBrowse = newVal
                                    onUseLoginForBrowseChange(newVal)
                                }
                            ),
                            Material3SettingsItem(
                                title = { Text("Sincronización con YouTube Music") },
                                icon = painterResource(R.drawable.cached),
                                trailingContent = {
                                    Switch(
                                        checked = ytmSync,
                                        onCheckedChange = onYtmSyncChange,
                                        modifier = Modifier.scale(0.8f)
                                    )
                                },
                                onClick = { onYtmSyncChange(!ytmSync) }
                            )
                        )
                    )
                }

                Material3SettingsGroup(
                    title = "App",
                    compact = true,
                    items = listOf(
                        Material3SettingsItem(
                            title = { Text("Ajustes") },
                            icon = painterResource(R.drawable.settings),
                            onClick = { onNavigate("settings") }
                        ),
                        Material3SettingsItem(
                            title = { Text("Acerca de") },
                            icon = painterResource(R.drawable.info),
                            trailingContent = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { onNavigate("settings/about") }
                        )
                    )
                )

                // Footer links (Privacy Policy / Terms of Service) hidden per user request.
            }
        }
    }
}
