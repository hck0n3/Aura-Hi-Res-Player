package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import iad1tya.echo.music.ui.newui.AuraDialogWindowEffects
import iad1tya.echo.music.ui.newui.AuraFloatingSurface
import iad1tya.echo.music.ui.newui.AuraPanel
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.newui.rememberUnreadOwnerNoticesCount
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.AccountSettingsViewModel
import iad1tya.echo.music.viewmodels.HomeViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * Account flyout from the avatar / gear. With "Interfaz nueva" ON it uses the same frosted
 * translucent plate as other premium dialogs ([AuraFloatingSurface] + [AuraDialogWindowEffects])
 * so the home behind shows through — owner request for launch.
 */
@Composable
fun SettingDialoge(
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    homeViewModel: HomeViewModel,
) {
    val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        innerTubeCookie.isNotEmpty() && "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val (accountEmail, _) = rememberPreference(AccountEmailKey, "")
    val accountName by homeViewModel.accountName.collectAsState()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, false)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val onBrowseLoginChange: (Boolean) -> Unit = { enabled ->
        com.music.innertube.YouTube.useLoginForBrowse = enabled
        onUseLoginForBrowseChange(enabled)
        if (enabled && isLoggedIn) accountSettingsViewModel.syncAll()
    }

    val skin = rememberAuraPanelSkin()
    val primaryColor = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val premium = skin.enabled && skin.darkGround
    val unreadNotices = rememberUnreadOwnerNoticesCount()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (premium) {
            AuraDialogWindowEffects(enabled = true)
            AuraFloatingSurface(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                shape = AuraShapes.Card,
            ) {
                SettingDialogeBody(
                    isLoggedIn = isLoggedIn,
                    accountName = accountName,
                    accountEmail = accountEmail,
                    accountImageUrl = accountImageUrl,
                    unreadNotices = unreadNotices,
                    primaryColor = primaryColor,
                    mutedColor = mutedColor,
                    titleStyle = true,
                    useLoginForBrowse = useLoginForBrowse,
                    onUseLoginForBrowseChange = onBrowseLoginChange,
                    ytmSync = ytmSync,
                    onYtmSyncChange = onYtmSyncChange,
                    onDismissRequest = onDismissRequest,
                    onNavigate = onNavigate,
                )
            }
        } else {
            AuraPanel(
                skin = skin,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                classicShape = RoundedCornerShape(28.dp),
                classicColors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                SettingDialogeBody(
                    isLoggedIn = isLoggedIn,
                    accountName = accountName,
                    accountEmail = accountEmail,
                    accountImageUrl = accountImageUrl,
                    unreadNotices = unreadNotices,
                    primaryColor = primaryColor,
                    mutedColor = mutedColor,
                    titleStyle = skin.enabled,
                    useLoginForBrowse = useLoginForBrowse,
                    onUseLoginForBrowseChange = onBrowseLoginChange,
                    ytmSync = ytmSync,
                    onYtmSyncChange = onYtmSyncChange,
                    onDismissRequest = onDismissRequest,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun SettingDialogeBody(
    isLoggedIn: Boolean,
    accountName: String,
    accountEmail: String,
    accountImageUrl: String?,
    unreadNotices: Int,
    primaryColor: androidx.compose.ui.graphics.Color,
    mutedColor: androidx.compose.ui.graphics.Color,
    titleStyle: Boolean,
    useLoginForBrowse: Boolean,
    onUseLoginForBrowseChange: (Boolean) -> Unit,
    ytmSync: Boolean,
    onYtmSyncChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val hasUnread = unreadNotices > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            Spacer(modifier = Modifier.size(24.dp))

            Text(
                text = "Aura Hi-Res Player",
                style = if (titleStyle) {
                    AuraType.SheetTitle
                } else {
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                },
                color = primaryColor,
                textAlign = TextAlign.Center,
            )

            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.close),
                    contentDescription = "Cerrar",
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (isLoggedIn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                BadgedBox(
                    badge = {
                        if (hasUnread) {
                            Badge(containerColor = MaterialTheme.colorScheme.error)
                        }
                    },
                ) {
                    AsyncImage(
                        model = accountImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = accountName.ifBlank { stringResource(R.string.account) },
                        style = if (titleStyle) AuraType.RowTitle else MaterialTheme.typography.titleMedium,
                        color = primaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (accountEmail.isNotBlank()) {
                        Text(
                            text = accountEmail,
                            style = if (titleStyle) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                            color = mutedColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Material3SettingsGroup(
                title = "Preferencias",
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
                                modifier = Modifier.scale(0.8f),
                            )
                        },
                        onClick = {
                            val newVal = !useLoginForBrowse
                            com.music.innertube.YouTube.useLoginForBrowse = newVal
                            onUseLoginForBrowseChange(newVal)
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text("Sincronización con YouTube Music") },
                        icon = painterResource(R.drawable.cached),
                        trailingContent = {
                            Switch(
                                checked = ytmSync,
                                onCheckedChange = onYtmSyncChange,
                                modifier = Modifier.scale(0.8f),
                            )
                        },
                        onClick = { onYtmSyncChange(!ytmSync) },
                    ),
                ),
            )
        } else {
            Material3SettingsGroup(
                title = "Cuenta",
                compact = true,
                items = listOf(
                    Material3SettingsItem(
                        title = { Text("Iniciar sesión") },
                        icon = painterResource(R.drawable.login),
                        onClick = { onNavigate("login") },
                    ),
                ),
            )
        }

        Material3SettingsGroup(
            title = "App",
            compact = true,
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.owner_notices_title)) },
                    description = {
                        Text(stringResource(R.string.owner_notices_settings_desc))
                    },
                    icon = painterResource(R.drawable.notification),
                    showBadge = hasUnread,
                    trailingContent = {
                        if (hasUnread) {
                            Text(
                                text = unreadNotices.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    onClick = { onNavigate("settings/notices") },
                ),
                Material3SettingsItem(
                    title = { Text("Ajustes") },
                    icon = painterResource(R.drawable.settings),
                    onClick = { onNavigate("settings") },
                ),
                Material3SettingsItem(
                    title = { Text("Acerca de") },
                    icon = painterResource(R.drawable.info),
                    trailingContent = {
                        Text(
                            BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedColor,
                        )
                    },
                    onClick = { onNavigate("settings/about") },
                ),
            ),
        )
    }
}
