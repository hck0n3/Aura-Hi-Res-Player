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
import iad1tya.echo.music.ui.utils.backToMain
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.qobuz_title)) },
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
                text = stringResource(R.string.qobuz_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.linked) {
                ConnectedCard(
                    state = state,
                    onToggle = viewModel::setUseOwnSubscription,
                    onLogout = viewModel::logout,
                )
            } else {
                LoginCard(
                    loading = state.loading,
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
                ErrorCard(text = stringResource(messageRes), onDismiss = viewModel::dismissError)
            }
        }
    }
}

@Composable
private fun ConnectedCard(
    state: QobuzLoginViewModel.UiState,
    onToggle: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onLogout) { Text(stringResource(R.string.qobuz_logout)) }
            }

            state.email?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = if (state.tierLabel.isNullOrBlank()) {
                    if (state.freeOrLossyOnly) stringResource(R.string.qobuz_tier_lossy)
                    else stringResource(R.string.qobuz_hires_ready)
                } else {
                    stringResource(R.string.qobuz_tier, state.tierLabel)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.freeOrLossyOnly) {
                Text(
                    text = stringResource(R.string.qobuz_free_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                // Honest follow-up: the switch below was deliberately NOT turned on for this plan.
                if (state.autoEnableSkipped) {
                    Text(
                        text = stringResource(R.string.qobuz_free_not_enabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.qobuz_use_own_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.qobuz_use_own_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.useOwnSubscription, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun LoginCard(
    loading: Boolean,
    token: String,
    onTokenChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onLoginToken: () -> Unit,
    onLoginPassword: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.qobuz_login_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            // Recommended path: paste an existing user_auth_token.
            Text(
                text = stringResource(R.string.qobuz_token_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                shape = RoundedCornerShape(16.dp),
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

            HorizontalDivider()
            Text(
                text = stringResource(R.string.qobuz_or_password),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.qobuz_login))
            }
        }
    }
}

@Composable
private fun ErrorCard(text: String, onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
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
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        }
    }
}
