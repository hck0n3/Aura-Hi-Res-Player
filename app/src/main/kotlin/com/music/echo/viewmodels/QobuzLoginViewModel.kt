package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.UseOwnQobuzHiResKey
import iad1tya.echo.music.qobuz.QobuzAuthenticator
import iad1tya.echo.music.qobuz.QobuzHiRes
import iad1tya.echo.music.qobuz.QobuzLoginResult
import iad1tya.echo.music.qobuz.QobuzTokenStore
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Qobuz link screen (Ajustes ▸ Cuentas ▸ Qobuz). Two ways in: paste a user_auth_token, or
 * email+password. Either way [QobuzAuthenticator] validates against the REAL Qobuz API and returns a
 * session with the working app_id/app_secret, which is persisted in the encrypted [QobuzTokenStore].
 *
 * THREADING: nothing here touches the vault synchronously. [QobuzTokenStore] is `suspend` end to end
 * (AndroidKeyStore master key + encrypted file = real I/O), and every entry point below runs it inside
 * [viewModelScope] — the same shape MigrationViewModel uses for TidalTokenStore. Doing it from `init`
 * directly would run the keystore build during composition, on Main.
 *
 * On a successful link the own-subscription playback path is turned ON automatically ONLY when the account
 * can actually deliver hi-res. A free / lossy-only plan is left OFF with an honest note: Qobuz downgrades
 * server-side, so that plan's stream would be rejected by the resolver anyway (it only accepts FLAC), and
 * silently flipping a toggle that cannot produce a single hi-res second is a promise the plan can't keep.
 */
@HiltViewModel
class QobuzLoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authenticator: QobuzAuthenticator,
    private val tokenStore: QobuzTokenStore,
) : ViewModel() {

    data class UiState(
        val linked: Boolean = false,
        val loading: Boolean = false,
        val email: String? = null,
        val tierLabel: String? = null,
        /** True when the linked account can't do 24-bit (free or lossless-only) — UI shows an honest note. */
        val freeOrLossyOnly: Boolean = false,
        val useOwnSubscription: Boolean = false,
        /** True right after a link that was NOT auto-enabled because the plan has no hi-res entitlement. */
        val autoEnableSkipped: Boolean = false,
        /** String RESOURCE id, so the message follows the app locale (3-locale parity). */
        @StringRes val error: Int? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Make sure the playback holder is bound to the same vault (App also does this at startup).
        // attach() is a pure field assignment — it never opens the vault, so it is safe on Main.
        QobuzHiRes.attach(tokenStore)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // session() hops to Dispatchers.IO itself; the read never blocks composition.
            val session = tokenStore.session()
            _uiState.value = _uiState.value.copy(
                linked = session != null,
                email = session?.email,
                tierLabel = session?.tierLabel,
                freeOrLossyOnly = session?.hiresEntitled == false,
                useOwnSubscription = QobuzHiRes.enabled,
                error = null,
            )
        }
    }

    fun loginWithPassword(email: String, password: String) {
        val e = email.trim()
        if (e.isBlank() || password.isBlank()) return
        authenticate { authenticator.authenticate(e, password) }
    }

    fun loginWithToken(token: String) {
        val t = token.trim()
        if (t.isBlank()) return
        authenticate { authenticator.authenticateWithToken(t) }
    }

    private fun authenticate(block: suspend () -> QobuzLoginResult) {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = block()) {
                is QobuzLoginResult.Success -> {
                    tokenStore.save(result.session)
                    QobuzHiRes.attach(tokenStore)
                    // Auto-enable ONLY for a plan that can actually deliver 24-bit FLAC.
                    val autoEnable = !result.freeOrLossyOnly
                    QobuzHiRes.enabled = autoEnable
                    context.dataStore.edit { it[UseOwnQobuzHiResKey] = autoEnable }
                    _uiState.value = UiState(
                        linked = true,
                        loading = false,
                        email = result.session.email,
                        tierLabel = result.session.tierLabel,
                        freeOrLossyOnly = result.freeOrLossyOnly,
                        useOwnSubscription = autoEnable,
                        autoEnableSkipped = !autoEnable,
                        error = null,
                    )
                }

                is QobuzLoginResult.Error ->
                    _uiState.value = _uiState.value.copy(loading = false, error = result.messageRes)
            }
        }
    }

    fun setUseOwnSubscription(enabled: Boolean) {
        QobuzHiRes.enabled = enabled
        // An explicit user choice clears the "we didn't turn this on for you" note.
        _uiState.value = _uiState.value.copy(useOwnSubscription = enabled, autoEnableSkipped = false)
        viewModelScope.launch { context.dataStore.edit { it[UseOwnQobuzHiResKey] = enabled } }
    }

    fun logout() {
        QobuzHiRes.enabled = false
        _uiState.value = UiState()
        viewModelScope.launch {
            tokenStore.logout()
            context.dataStore.edit { it[UseOwnQobuzHiResKey] = false }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
