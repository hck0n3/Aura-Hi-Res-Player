package iad1tya.echo.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import iad1tya.echo.music.utils.SupportContact
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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tight = maxHeight < 720.dp
            val outerPad = if (tight) 12.dp else 24.dp
            val body: @Composable () -> Unit = {
                SettingDialogeBody(
                    isLoggedIn = isLoggedIn,
                    accountName = accountName,
                    accountEmail = accountEmail,
                    accountImageUrl = accountImageUrl,
                    unreadNotices = unreadNotices,
                    primaryColor = primaryColor,
                    mutedColor = mutedColor,
                    titleStyle = premium || skin.enabled,
                    useLoginForBrowse = useLoginForBrowse,
                    onUseLoginForBrowseChange = onBrowseLoginChange,
                    ytmSync = ytmSync,
                    onYtmSyncChange = onYtmSyncChange,
                    onDismissRequest = onDismissRequest,
                    onNavigate = onNavigate,
                    tight = tight,
                    allowScroll = maxHeight < 560.dp,
                )
            }
            if (premium) {
                AuraDialogWindowEffects(enabled = true)
                AuraFloatingSurface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(outerPad)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight - outerPad * 2),
                    shape = AuraShapes.Card,
                ) { body() }
            } else {
                AuraPanel(
                    skin = skin,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(outerPad)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight - outerPad * 2),
                    classicShape = RoundedCornerShape(28.dp),
                    classicColors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) { body() }
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
    tight: Boolean,
    allowScroll: Boolean,
) {
    val hasUnread = unreadNotices > 0
    val context = LocalContext.current
    val vPad = if (tight) 8.dp else 16.dp
    val hPad = if (tight) 8.dp else 12.dp
    val gap = if (tight) 4.dp else 10.dp
    val avatar = if (tight) 36.dp else 48.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = vPad, horizontal = hPad),
        verticalArrangement = Arrangement.spacedBy(gap),
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
                    if (tight) AuraType.RowTitle else AuraType.SheetTitle
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
                            .size(avatar)
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

            var syncExpanded by rememberSaveable { mutableStateOf(false) }

            val prefItems = buildList {
                add(
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
                    )
                )
                add(
                    Material3SettingsItem(
                        title = { Text("Sincronización con YouTube Music") },
                        icon = painterResource(R.drawable.cached),
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Switch(
                                    checked = ytmSync,
                                    onCheckedChange = onYtmSyncChange,
                                    modifier = Modifier.scale(0.8f),
                                )
                                IconButton(
                                    onClick = { syncExpanded = !syncExpanded },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (syncExpanded) R.drawable.expand_less else R.drawable.expand_more
                                        ),
                                        contentDescription = if (syncExpanded) "Contraer sincronización" else "Expandir sincronización",
                                        tint = mutedColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        onClick = { syncExpanded = !syncExpanded },
                    )
                )
                if (syncExpanded) {
                    add(
                        Material3SettingsItem(
                            title = { Text("Sincronizar biblioteca ahora") },
                            description = {
                                Text(
                                    "Toda la biblioteca con tu cuenta.",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            icon = painterResource(R.drawable.sync),
                            onClick = {
                                iad1tya.echo.music.utils.YtmSyncWorker.enqueue(
                                    context,
                                    iad1tya.echo.music.utils.YtmSyncWorker.TYPE_ALL,
                                )
                                Toast.makeText(
                                    context,
                                    "Sincronizando toda la biblioteca… (continúa en segundo plano)",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onDismissRequest()
                            },
                        )
                    )
                    add(
                        Material3SettingsItem(
                            title = { Text("Programar sincronización") },
                            description = {
                                Text(
                                    "Cada 3 días por defecto, o elige cuándo",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            icon = painterResource(R.drawable.sync),
                            onClick = { onNavigate("settings/ytm_sync") },
                        )
                    )
                }
            }

            Material3SettingsGroup(
                title = "Preferencias",
                compact = true,
                items = prefItems,
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
                        Text(
                            stringResource(R.string.owner_notices_settings_desc),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                    title = { Text("Actualizaciones") },
                    description = {
                        Text(
                            "Cambios de esta versión y nuevas actualizaciones",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    icon = painterResource(R.drawable.update),
                    onClick = { onNavigate("settings/update") },
                ),
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.feedback_title)) },
                    description = {
                        Text(
                            stringResource(
                                R.string.feedback_settings_desc,
                                SupportContact.EMAIL,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    icon = painterResource(R.drawable.bug_report),
                    onClick = { onNavigate("settings/feedback") },
                ),
                Material3SettingsItem(
                    title = { Text("Ajustes") },
                    icon = painterResource(R.drawable.settings),
                    onClick = { onNavigate("settings") },
                ),
                Material3SettingsItem(
                    title = { Text("Compartir Aura Hi-Res") },
                    icon = painterResource(R.drawable.share),
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Aura Hi-Res Player")
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "Prueba Aura Hi-Res Player, el reproductor de música con la mejor calidad de audio:\n\nhttps://github.com/hck0n3/Aura-Hi-Res-Player/releases/latest"
                            )
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir Aura Hi-Res"))
                    },
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
