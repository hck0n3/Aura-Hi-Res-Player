

package iad1tya.echo.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.newui.AuraDialogWindowEffects
import iad1tya.echo.music.ui.newui.AuraFloatingSurface
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable

import kotlinx.coroutines.delay

// ──────────────────────────────────────────────────────────────────────────────────────────────────
// "Interfaz nueva" — [DefaultDialog] / [ListDialog] (and their callers [ActionPromptDialog] /
// [TextFieldDialog]) are the ONE dialog chrome ~44 call sites share: song/album/playlist menus, every
// settings confirmation, "Crear playlist" and "Agregar a playlist" among them. Retinting it here — the
// same seam philosophy as `Material3SettingsGroup` for settings rows and `Items.kt` for song rows —
// carries every one of those dialogs into the redesign at once, including ones inside ALREADY-ported
// screens (AuraLibraryScreen, AuraPlayerMenu, AuraMigrationScreen…) that were still popping a plain
// Material3 sheet on top of the new dark shell.
//
// Premium path: frosted translucent plate ([AuraFloatingSurface]) so the dimmed UI behind shows
// through — same language as sheets/menus. Account flyout stays opaque ([FloatingFill]) in
// SettingDialoge on purpose.
//
// With the flag OFF every branch below reduces to the exact original values — this is a strict
// superset of the previous behaviour, never a redesign of it.
// ──────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    val iconTint = if (premium) AuraPalette.Teal
    else AlertDialogDefaults.iconContentColor
    val titleTint = if (premium) AuraPalette.OnGround
    else AlertDialogDefaults.titleContentColor
    val buttonTint = if (premium) AuraPalette.Teal
    else MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AuraDialogWindowEffects(enabled = premium)
        AuraFloatingSurface(
            modifier = Modifier.padding(24.dp),
            shape = if (premium) AuraShapes.Card else AlertDialogDefaults.shape,
        ) {
            Column(
                horizontalAlignment = horizontalAlignment,
                modifier = modifier.padding(24.dp)
            ) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides iconTint) {
                        Box(Modifier.align(Alignment.CenterHorizontally)) { icon() }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides titleTint) {
                        ProvideTextStyle(
                            if (premium) AuraType.SheetTitle else MaterialTheme.typography.headlineSmall,
                        ) {
                            Box(
                                Modifier.align(
                                    if (icon == null) Alignment.Start else Alignment.CenterHorizontally,
                                ),
                            ) { title() }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                CompositionLocalProvider(
                    LocalContentColor provides if (premium) AuraPalette.OnGround else LocalContentColor.current,
                ) {
                    content()
                }

                if (buttons != null) {
                    Spacer(Modifier.height(24.dp))
                    FlowRow(modifier = Modifier.align(Alignment.End)) {
                        CompositionLocalProvider(LocalContentColor provides buttonTint) {
                            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                                buttons()
                            }
                        }
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPromptDialog(
    title: String? = null,
    titleBar: @Composable (RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = if (titleBar != null) {
            { Row { titleBar() } }
        } else if (title != null) {
            {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        } else null,
        buttons = {
            if (onReset != null) {
                Row(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { onReset() },
                        modifier = Modifier.tvFocusable(rememberIsTvOrCar(), scaleFocused = 1f),
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }

            if (onCancel != null) {
                TextButton(
                    onClick = { onCancel() },
                    modifier = Modifier.tvFocusable(rememberIsTvOrCar(), scaleFocused = 1f),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }

            TextButton(
                onClick = { onConfirm() },
                modifier = Modifier.tvFocusable(rememberIsTvOrCar(), scaleFocused = 1f),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        }
    ) {
        content()
    }
}

@Composable
fun ListDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AuraDialogWindowEffects(enabled = premium)
        AuraFloatingSurface(
            modifier = Modifier.padding(24.dp),
            shape = if (premium) AuraShapes.Card else AlertDialogDefaults.shape,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier
                    .padding(vertical = 24.dp)
                    .imePadding(),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides if (premium) AuraPalette.OnGround else LocalContentColor.current,
                ) {
                    LazyColumn(content = content)
                }
            }
        }
    }
}

@Composable
fun InfoLabel(
    text: String
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = 8.dp)
) {
    Icon(
        painter = painterResource(id = R.drawable.info),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(4.dp)
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    initialTextFieldValue: TextFieldValue = TextFieldValue(),
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    autoFocus: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (String) -> Unit = {},

    
    textFields: List<Pair<String, TextFieldValue>>? = null,
    onTextFieldsChange: ((Int, TextFieldValue) -> Unit)? = null,
    onDoneMultiple: ((List<String>) -> Unit)? = null,

    onDismiss: () -> Unit,
    autoDismiss: Boolean = true,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val legacyFieldState = remember { mutableStateOf(initialTextFieldValue) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        icon = icon,
        title = title,
        buttons = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.tvFocusable(rememberIsTvOrCar(), scaleFocused = 1f),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }

            val isValid = textFields?.all { isInputValid(it.second.text) }
                ?: isInputValid(legacyFieldState.value.text)

            TextButton(
                enabled = isValid,
                modifier = Modifier.tvFocusable(rememberIsTvOrCar(), scaleFocused = 1f),
                onClick = {
                    if (autoDismiss) onDismiss()
                    if (textFields != null && onDoneMultiple != null) {
                        onDoneMultiple(textFields.map { it.second.text })
                    } else {
                        onDone(legacyFieldState.value.text)
                    }
                }
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        }
    ) {
        Column(
            modifier = Modifier.weight(weight = 1f, fill = false)
        ) {
            if (textFields != null) {
                textFields.forEachIndexed { index, (label, value) ->
                    TextField(
                        value = value,
                        onValueChange = { onTextFieldsChange?.invoke(index, it) },
                        placeholder = { Text(label) },
                        singleLine = singleLine,
                        maxLines = maxLines,
                        colors = OutlinedTextFieldDefaults.colors(),
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (singleLine) ImeAction.Done else ImeAction.None,
                            keyboardType = keyboardType
                        ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (onDoneMultiple != null) {
                                onDoneMultiple(textFields.map { it.second.text })
                                if (autoDismiss) onDismiss()
                            }
                        }
                    ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index < textFields.size - 1) 12.dp else 0.dp)
                            .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                    )
                }
            } else {
                TextField(
                    value = legacyFieldState.value,
                    onValueChange = { legacyFieldState.value = it },
                    placeholder = placeholder,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    colors = OutlinedTextFieldDefaults.colors(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (singleLine) ImeAction.Done else ImeAction.None,
                        keyboardType = keyboardType
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onDone(legacyFieldState.value.text)
                            if (autoDismiss) onDismiss()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            extraContent?.invoke()
        }
    }
}
