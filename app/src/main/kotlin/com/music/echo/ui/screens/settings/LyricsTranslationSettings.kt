

package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AskTranslateLyricsOnOpenKey
import iad1tya.echo.music.constants.AutoTranslateLyricsKey
import iad1tya.echo.music.constants.LanguageCodeToName
import iad1tya.echo.music.constants.TranslateLanguageKey
import iad1tya.echo.music.ui.component.EnumDialog
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.utils.rememberPreference

/**
 * Ajustes de traducción de letras — solo Google Translate (gratis, sin clave, sin IA). Reemplaza la
 * antigua pantalla de proveedores de IA (OpenRouter/Mistral/DeepL): el idioma destino y los dos
 * interruptores de traducción automática son lo único que sigue siendo configurable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsTranslationSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var translateLanguage by rememberPreference(TranslateLanguageKey, "es-419")
    var autoTranslateLyrics by rememberPreference(AutoTranslateLyricsKey, true)
    var askTranslateOnOpen by rememberPreference(AskTranslateLyricsOnOpenKey, false)

    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }

    if (showLanguageDialog) {
        EnumDialog(
            onDismiss = { showLanguageDialog = false },
            onSelect = {
                translateLanguage = it
                showLanguageDialog = false
            },
            title = stringResource(R.string.ai_target_language),
            current = translateLanguage,
            values = LanguageCodeToName.keys.sortedBy { LanguageCodeToName[it] },
            valueText = { LanguageCodeToName[it] ?: it },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.ai_lyrics_translation),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.ai_target_language)) },
                    description = { Text(LanguageCodeToName[translateLanguage] ?: translateLanguage) },
                    onClick = { showLanguageDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text("Traducir letra automáticamente") },
                    description = {
                        Text("Al abrir la letra, Aura la traduce al idioma elegido arriba sin preguntar.")
                    },
                    onClick = { autoTranslateLyrics = !autoTranslateLyrics },
                    trailingContent = {
                        Switch(
                            checked = autoTranslateLyrics,
                            onCheckedChange = { autoTranslateLyrics = it },
                        )
                    },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text("Preguntar traducir la letra al abrir") },
                    description = {
                        Text("Cuando abras la letra de una canción en inglés, Aura te preguntará si quieres traducirla (solo si la traducción automática está apagada).")
                    },
                    onClick = { askTranslateOnOpen = !askTranslateOnOpen },
                    trailingContent = {
                        Switch(
                            checked = askTranslateOnOpen,
                            onCheckedChange = { askTranslateOnOpen = it },
                        )
                    },
                ),
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.ai_lyrics_translation)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = null,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
