

package iad1tya.echo.music.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.constants.*
import iad1tya.echo.music.ui.component.*
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.AccountSettingsViewModel
import iad1tya.echo.music.viewmodels.HomeViewModel
import iad1tya.echo.music.R
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountSettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, false)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsState()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showMirrorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            
            Material3SettingsGroup(
                title = stringResource(R.string.settings),
                items = listOf(
                    Material3SettingsItem(
                        icon = if (isLoggedIn && !accountImageUrl.isNullOrBlank()) null else painterResource(R.drawable.login),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLoggedIn && !accountImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = accountImageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Text(
                                    text = if (isLoggedIn) accountName else stringResource(R.string.login),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        trailingContent = if (isLoggedIn) ({
                            OutlinedButton(




                                onClick = {
                                    showLogoutDialog = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(R.string.action_logout))
                            }
                        }) else null,
                        onClick = {
                            if (isLoggedIn) navController.navigate("account")
                            else navController.navigate("login")
                        }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            
            if (isLoggedIn) {
                Material3SettingsGroup(
                    title = stringResource(R.string.settings_section_player_content),
                    items = listOf(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.add_circle),
                            title = { Text(stringResource(R.string.more_content)) },
                            trailingContent = {
                                Switch(
                                    checked = useLoginForBrowse,
                                    onCheckedChange = {
                                        YouTube.useLoginForBrowse = it
                                        onUseLoginForBrowseChange(it)
                                    },
                                    thumbContent = {
                                        Icon(
                                            painter = painterResource(
                                                id = if (useLoginForBrowse) R.drawable.check else R.drawable.close
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    }
                                )
                            },
                            onClick = {
                                val newValue = !useLoginForBrowse
                                YouTube.useLoginForBrowse = newValue
                                onUseLoginForBrowseChange(newValue)
                            }
                        ),
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.cached),
                            title = { Text(stringResource(R.string.yt_sync)) },
                            trailingContent = {
                                Switch(
                                    checked = ytmSync,
                                    onCheckedChange = onYtmSyncChange,
                                    thumbContent = {
                                        Icon(
                                            painter = painterResource(
                                                id = if (ytmSync) R.drawable.check else R.drawable.close
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    }
                                )
                            },
                            onClick = { onYtmSyncChange(!ytmSync) }
                        ),
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.sync),
                            title = { Text("Espejar favoritos desde mi cuenta") },
                            description = { Text("Deja tus favoritos del app idénticos a los de tu cuenta de YouTube. Puede quitar los que ya no estén en tu cuenta.") },
                            onClick = { showMirrorDialog = true }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showLogoutDialog) {
            DefaultDialog(
                onDismiss = { showLogoutDialog = false },
                title = { Text(stringResource(R.string.logout_dialog_title)) },
                buttons = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        
                        ToggleButton(
                            checked = false,
                            onCheckedChange = { showLogoutDialog = false },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }

                        
                        ToggleButton(
                            checked = false,
                            onCheckedChange = {
                                accountSettingsViewModel.logoutAndClearSyncedContent(context, onInnerTubeCookieChange)
                                showLogoutDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.logout_clear_data))
                        }

                        
                        ToggleButton(
                            checked = true,
                            onCheckedChange = {
                                accountSettingsViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                                showLogoutDialog = false
                                navController.navigateUp()
                            },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                        ) {
                            Text(stringResource(R.string.logout_keep_data))
                        }
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.logout_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        if (showMirrorDialog) {
            DefaultDialog(
                onDismiss = { showMirrorDialog = false },
                title = { Text("Espejar favoritos desde mi cuenta") },
                buttons = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        ToggleButton(
                            checked = false,
                            onCheckedChange = { showMirrorDialog = false },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }

                        ToggleButton(
                            checked = true,
                            onCheckedChange = {
                                accountSettingsViewModel.mirrorFromAccount()
                                Toast.makeText(
                                    context,
                                    "Sincronizando favoritos con tu cuenta…",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showMirrorDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                        ) {
                            Text("Espejar")
                        }
                    }
                }
            ) {
                Text(
                    text = "Esto dejará tus favoritos del app idénticos a los de tu cuenta de YouTube: añade los que falten y quita los que ya no estén en tu cuenta. Tu cuenta de YouTube no se modifica. ¿Continuar?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}
