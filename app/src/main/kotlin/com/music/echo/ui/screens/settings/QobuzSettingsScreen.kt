@file:OptIn(ExperimentalMaterial3Api::class)

package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanel
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraSwitch
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.viewmodels.QobuzLoginViewModel

/**
 * Ajustes ▸ Cuentas ▸ Qobuz — link the owner's OWN Qobuz subscription to stream hi-res FLAC.
 *
 * Two ways in: paste a `user_auth_token` (recommended, no password leaves the device to anyone but Qobuz)
 * or email + password. The result is shown honestly: which account, the tier, and — for a free/lossy
 * account — that 24-bit needs Qobuz Studio/Sublime.
 */
@Composable
fun QobuzSettingsScreen(navController: NavController) {
    val viewModel: QobuzLoginViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    var tokenField by rememberSaveable { mutableStateOf("") }
    var emailField by rememberSaveable { mutableStateOf("") }
    var passwordField by rememberSaveable { mutableStateOf("") }

    // ONE flag read for the whole screen; the three cards take it as a parameter.
    val skin = rememberAuraPanelSkin()
    val ground = if (skin.enabled && skin.darkGround) AuraPalette.Ground else MaterialTheme.colorScheme.background

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // `MaterialTheme.colorScheme.background` IS the `Scaffold` default, so the classic path is
        // unchanged; only a dark-themed new-UI run gets the render's ground.
        containerColor = ground,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.qobuz_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = null,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), null)
                    }
                },
                colors = if (skin.enabled && skin.darkGround) {
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = ground,
                        scrolledContainerColor = AuraPalette.GroundRaised,
                        titleContentColor = skin.ink,
                        navigationIconContentColor = skin.ink,
                    )
                } else {
                    // The `LargeTopAppBar` default, spelled out so the classic bar is unchanged.
                    TopAppBarDefaults.largeTopAppBarColors()
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
                text = stringResource(R.string.qobuz_intro),
                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.linked) {
                ConnectedCard(
                    state = state,
                    skin = skin,
                    onToggle = viewModel::setUseOwnSubscription,
                    onLogout = viewModel::logout,
                )
            } else {
                LoginCard(
                    loading = state.loading,
                    skin = skin,
                    token = tokenField,
                    onTokenChange = { tokenField = it },
                    email = emailField,
                    onEmailChange = { emailField = it },
                    password = passwordField,
                    onPasswordChange = { passwordField = it },
                    onLoginToken = { viewModel.loginWithToken(tokenField) },
                    onLoginPassword = { viewModel.loginWithPassword(emailField, passwordField) },
                )
            }

            state.error?.let { messageRes ->
                ErrorCard(text = stringResource(messageRes), skin = skin, onDismiss = viewModel::dismissError)
            }
        }
    }
}

@Composable
private fun ConnectedCard(
    state: QobuzLoginViewModel.UiState,
    skin: AuraPanelSkin,
    onToggle: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    AuraPanel(
        skin = skin,
        classicShape = RoundedCornerShape(20.dp),
        // `CardDefaults.cardColors()` IS what this `Card` was using; spelled out so the classic card
        // is byte-identical.
        classicColors = CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.qobuz_connected),
                    style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleSmall,
                    fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
                    color = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onLogout) { Text(stringResource(R.string.qobuz_logout)) }
            }

            state.email?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyMedium,
                    color = if (skin.enabled) skin.ink else Color.Unspecified,
                )
            }
            Text(
                text = if (state.tierLabel.isNullOrBlank()) {
                    if (state.freeOrLossyOnly) stringResource(R.string.qobuz_tier_lossy)
                    else stringResource(R.string.qobuz_hires_ready)
                } else {
                    stringResource(R.string.qobuz_tier, state.tierLabel)
                },
                // The tier is technical data — the render sets that in tracked monospace.
                style = if (skin.enabled) AuraType.Technical else MaterialTheme.typography.bodySmall,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.freeOrLossyOnly) {
                Text(
                    text = stringResource(R.string.qobuz_free_note),
                    style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
                    // Stays the theme's error colour on both paths: this is a warning, not decoration.
                    color = MaterialTheme.colorScheme.error,
                )
                // Honest follow-up: the switch below was deliberately NOT turned on for this plan.
                if (state.autoEnableSkipped) {
                    Text(
                        text = stringResource(R.string.qobuz_free_not_enabled),
                        style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
                        color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (skin.enabled) {
                HorizontalDivider(thickness = 1.dp, color = skin.hairline)
            } else {
                HorizontalDivider()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.qobuz_use_own_title),
                        style = if (skin.enabled) AuraType.MenuLabel else MaterialTheme.typography.bodyLarge,
                        color = if (skin.enabled) skin.ink else Color.Unspecified,
                    )
                    Text(
                        text = stringResource(R.string.qobuz_use_own_desc),
                        style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
                        color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The render's own switch (teal pill, 48 dp touch target) instead of the Material one.
                // Same `onToggle`, same state — chrome only. Dark ground ONLY: `AuraSwitch` paints a
                // fixed teal track and a `#061018` knob, which is a dark-theme object; on a light
                // theme the Material switch stays, so the control keeps following the user's scheme.
                if (skin.enabled && skin.darkGround) {
                    AuraSwitch(
                        checked = state.useOwnSubscription,
                        onCheckedChange = onToggle,
                        contentDescription = stringResource(R.string.qobuz_use_own_title),
                    )
                } else {
                    Switch(checked = state.useOwnSubscription, onCheckedChange = onToggle)
                }
            }
        }
    }
}

@Composable
private fun LoginCard(
    loading: Boolean,
    skin: AuraPanelSkin,
    token: String,
    onTokenChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onLoginToken: () -> Unit,
    onLoginPassword: () -> Unit,
) {
    AuraPanel(
        skin = skin,
        classicShape = RoundedCornerShape(20.dp),
        classicColors = CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.qobuz_login_title),
                style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleSmall,
                fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
                color = if (skin.enabled) skin.inkFaint else Color.Unspecified,
            )

            // Recommended path: paste an existing user_auth_token.
            Text(
                text = stringResource(R.string.qobuz_token_hint),
                style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                singleLine = true,
                enabled = !loading,
                label = { Text(stringResource(R.string.qobuz_token_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onLoginToken,
                enabled = !loading && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                // The render's action buttons are fully round pills. The COLOURS stay the theme's, so
                // the button keeps following the user's accent instead of hard-coding one.
                shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.qobuz_use_token))
                }
            }

            if (skin.enabled) {
                HorizontalDivider(thickness = 1.dp, color = skin.hairline)
            } else {
                HorizontalDivider()
            }
            Text(
                text = stringResource(R.string.qobuz_or_password),
                style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                singleLine = true,
                enabled = !loading,
                label = { Text(stringResource(R.string.qobuz_email_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                singleLine = true,
                enabled = !loading,
                label = { Text(stringResource(R.string.qobuz_password_label)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = onLoginPassword,
                enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.qobuz_login))
            }
        }
    }
}

/**
 * Deliberately NOT an [AuraPanel]: this card's fill carries meaning (the theme's `errorContainer`),
 * and a neutral white wash would turn an error into decoration. Only its radius and type follow the
 * redesign.
 */
@Composable
private fun ErrorCard(text: String, skin: AuraPanelSkin, onDismiss: () -> Unit) {
    Card(
        shape = if (skin.enabled) AuraShapes.Card else RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = text,
                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        }
    }
}
