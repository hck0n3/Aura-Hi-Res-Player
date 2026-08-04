@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package iad1tya.echo.music.ui.screens.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AccentVividnessKey
import iad1tya.echo.music.constants.AppThemePresetKey
import iad1tya.echo.music.constants.CustomAccentColorKey
import iad1tya.echo.music.constants.DarkModeKey
import iad1tya.echo.music.constants.DynamicThemeKey
import iad1tya.echo.music.constants.PureBlackKey
import iad1tya.echo.music.constants.PureBlackMiniPlayerKey
import iad1tya.echo.music.constants.SelectedThemeColorKey
import iad1tya.echo.music.ui.component.ColorPickerConversions
import iad1tya.echo.music.ui.theme.AccentVividness
import iad1tya.echo.music.ui.theme.DefaultThemeColor
import iad1tya.echo.music.ui.theme.MuestreoBlue
import iad1tya.echo.music.ui.theme.MuestreoDarkColorScheme
import iad1tya.echo.music.ui.theme.MuestreoGradientStops
import iad1tya.echo.music.ui.theme.ThemePreset
import iad1tya.echo.music.ui.theme.onColorFor
import iad1tya.echo.music.ui.theme.withAccent
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference

data class ThemePalette(
    val nameRes: Int,
    val seedColor: Color,
    /**
     * A named, hand-authored scheme instead of a seed pushed through the tonal generator. [ThemePreset.NONE]
     * (the default) keeps the entry behaving exactly as every existing swatch does; [seedColor] is
     * still stored for a preset, so the accent stays on-brand even where the preset is not consulted.
     */
    val preset: ThemePreset = ThemePreset.NONE,
)

/**
 * Accent presets, deliberately spread across the WHOLE hue circle at three different intensities
 * instead of the single mid-tone Material 500/600 band this list used to be.
 *
 * A wider list alone does NOT make the app look more colourful — `PaletteStyle.TonalSpot` re-derives
 * every seed at a fixed chroma, so all of these render as the same pastel until the user raises
 * "colour intensity" (see [iad1tya.echo.music.ui.theme.AccentVividness]). The two go together.
 *
 * Existing entries keep their exact ARGB values: a stored accent is matched by value, so changing one
 * would silently unselect whatever the user already had.
 */
val PaletteColors = listOf(
    ThemePalette(R.string.palette_dynamic, Color.Transparent),
    // The app icon's own palette, as a complete scheme (surfaces included) rather than a seed — see
    // [iad1tya.echo.music.ui.theme.ThemePreset].
    ThemePalette(R.string.palette_muestreo, MuestreoBlue, ThemePreset.MUESTREO),
    ThemePalette(R.string.palette_crimson, Color(0xFFEC5464)),
    ThemePalette(R.string.palette_rose, Color(0xFFD81B60)),
    ThemePalette(R.string.palette_purple, Color(0xFF8E24AA)),
    ThemePalette(R.string.palette_deep_purple, Color(0xFF5E35B1)),
    ThemePalette(R.string.palette_indigo, Color(0xFF3949AB)),
    ThemePalette(R.string.palette_blue, Color(0xFF1E88E5)),
    ThemePalette(R.string.palette_sky_blue, Color(0xFF039BE5)),
    ThemePalette(R.string.palette_cyan, Color(0xFF00ACC1)),
    ThemePalette(R.string.palette_teal, Color(0xFF00897B)),
    ThemePalette(R.string.palette_green, Color(0xFF43A047)),
    ThemePalette(R.string.palette_light_green, Color(0xFF7CB342)),
    ThemePalette(R.string.palette_lime, Color(0xFFC0CA33)),
    ThemePalette(R.string.palette_yellow, Color(0xFFFDD835)),
    ThemePalette(R.string.palette_amber, Color(0xFFFFB300)),
    ThemePalette(R.string.palette_orange, Color(0xFFFB8C00)),
    ThemePalette(R.string.palette_deep_orange, Color(0xFFF4511E)),
    ThemePalette(R.string.palette_brown, Color(0xFF6D4C41)),
    ThemePalette(R.string.palette_grey, Color(0xFF757575)),
    ThemePalette(R.string.palette_blue_grey, Color(0xFF546E7A)),

    // Full-chroma tier: roughly every 30 deg of hue, at maximum saturation.
    ThemePalette(R.string.palette_vivid_red, Color(0xFFFF1744)),
    ThemePalette(R.string.palette_vivid_orange, Color(0xFFFF6D00)),
    ThemePalette(R.string.palette_vivid_amber, Color(0xFFFFAB00)),
    ThemePalette(R.string.palette_vivid_yellow, Color(0xFFFFEA00)),
    ThemePalette(R.string.palette_vivid_lime, Color(0xFFC6FF00)),
    ThemePalette(R.string.palette_vivid_green, Color(0xFF00C853)),
    ThemePalette(R.string.palette_vivid_mint, Color(0xFF1DE9B6)),
    ThemePalette(R.string.palette_vivid_cyan, Color(0xFF00E5FF)),
    ThemePalette(R.string.palette_vivid_azure, Color(0xFF2979FF)),
    ThemePalette(R.string.palette_vivid_violet, Color(0xFF651FFF)),
    ThemePalette(R.string.palette_vivid_magenta, Color(0xFFD500F9)),
    ThemePalette(R.string.palette_vivid_pink, Color(0xFFF50057)),

    // Deep / neutral tier: the dark and near-monochrome end the old list had no answer for.
    ThemePalette(R.string.palette_dark_red, Color(0xFF7F0000)),
    ThemePalette(R.string.palette_dark_amber, Color(0xFF7A4F01)),
    ThemePalette(R.string.palette_dark_green, Color(0xFF0C5227)),
    ThemePalette(R.string.palette_dark_teal, Color(0xFF00443F)),
    ThemePalette(R.string.palette_dark_navy, Color(0xFF0A2352)),
    ThemePalette(R.string.palette_dark_violet, Color(0xFF35156E)),
    ThemePalette(R.string.palette_dark_plum, Color(0xFF5A0F45)),
    ThemePalette(R.string.palette_charcoal, Color(0xFF23272B)),
    ThemePalette(R.string.palette_ivory, Color(0xFFF2EDE3)),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeScreen(
    navController: NavController,
) {
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val (pureBlack, onPureBlackChangeRaw) = rememberPreference(PureBlackKey, defaultValue = false)
    val (_, onPureBlackMiniPlayerChange) = rememberPreference(
        PureBlackMiniPlayerKey,
        defaultValue = false
    )

    val onPureBlackChange: (Boolean) -> Unit = { enabled ->
        onPureBlackChangeRaw(enabled)
        onPureBlackMiniPlayerChange(enabled)
    }
    val (selectedThemeColorInt, onSelectedThemeColorChange) = rememberPreference(
        SelectedThemeColorKey,
        DefaultThemeColor.toArgb()
    )
    val (_, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, defaultValue = true)
    val (accentVividness, onAccentVividnessChange) = rememberEnumPreference(
        AccentVividnessKey,
        defaultValue = AccentVividness.SOFT,
    )
    // 0 == the user has never typed a colour by hand. Only used to remember/restore what they typed.
    val (customAccentColorInt, onCustomAccentColorChange) = rememberPreference(
        CustomAccentColorKey,
        defaultValue = 0,
    )

    val (themePreset, onThemePresetChange) = rememberEnumPreference(
        AppThemePresetKey,
        defaultValue = ThemePreset.NONE,
    )

    val selectedThemeColor = Color(selectedThemeColorInt)
    val isTvOrCar = rememberIsTvOrCar()

    /**
     * The single selection channel every path goes through — a swatch, Dynamic, or a typed hex.
     * Clearing the preset HERE (rather than in each caller) is what makes a named theme fully
     * reversible: there is no way to choose another colour that leaves the preset stuck on.
     */
    val handleColorSelection: (Color, ThemePreset) -> Unit = { color, preset ->
        onSelectedThemeColorChange(color.toArgb())
        onDynamicThemeChange(color == DefaultThemeColor && preset == ThemePreset.NONE)
        onThemePresetChange(preset)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theme_colors), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.theme_mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ThemeModeCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.dark_theme_follow_system),
                            icon = Icons.Rounded.BrightnessAuto,
                            // Mutually exclusive with AMOLED: pureBlack forces a dark look, so it can never
                            // be "follow system" at the same time. Without !pureBlack this card lit up
                            // together with AMOLED.
                            isSelected = darkMode == DarkMode.AUTO && !pureBlack,
                            onClick = { onDarkModeChange(DarkMode.AUTO); onPureBlackChange(false) }
                        )
                        ThemeModeCard(
                            modifier = Modifier.weight(1f),
                            title = "Light",
                            icon = Icons.Rounded.LightMode,
                            isSelected = darkMode == DarkMode.OFF && !pureBlack,
                            onClick = { onDarkModeChange(DarkMode.OFF); onPureBlackChange(false) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ThemeModeCard(
                            modifier = Modifier.weight(1f),
                            title = "Dark",
                            icon = Icons.Rounded.DarkMode,
                            isSelected = darkMode == DarkMode.ON && !pureBlack,
                            onClick = { onDarkModeChange(DarkMode.ON); onPureBlackChange(false) }
                        )
                        ThemeModeCard(
                            modifier = Modifier.weight(1f),
                            title = "AMOLED",
                            icon = Icons.Rounded.Contrast,
                            isSelected = pureBlack,
                            onClick = { onDarkModeChange(DarkMode.ON); onPureBlackChange(true) }
                        )
                    }
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }

            item {
                Text(
                    text = stringResource(R.string.color_palette),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        PaletteColors.forEach { palette ->
                            val isDynamicPalette = palette.seedColor == Color.Transparent
                            // The preset has to gate BOTH sides of this test, or "Muestreo" and the
                            // plain blue swatch (which share a seed) would light up together.
                            val isSelected = when {
                                palette.preset != ThemePreset.NONE -> themePreset == palette.preset
                                themePreset != ThemePreset.NONE -> false
                                isDynamicPalette -> selectedThemeColor == DefaultThemeColor
                                else -> selectedThemeColor == palette.seedColor
                            }

                            PaletteItem(
                                palette = palette,
                                isSelected = isSelected,
                                vividness = accentVividness,
                                isTvOrCar = isTvOrCar,
                                onClick = {
                                    val colorToSave = if (isDynamicPalette) DefaultThemeColor else palette.seedColor
                                    handleColorSelection(colorToSave, palette.preset)
                                }
                            )
                        }
                    }
                }
            }

            item {
                AccentIntensitySection(
                    seedColor = selectedThemeColor,
                    selected = accentVividness,
                    isTvOrCar = isTvOrCar,
                    presetActive = themePreset != ThemePreset.NONE,
                    onSelect = onAccentVividnessChange,
                )
            }

            item {
                CustomAccentSection(
                    selectedThemeColor = selectedThemeColor,
                    customAccentColorInt = customAccentColorInt,
                    isTvOrCar = isTvOrCar,
                    onApply = { color ->
                        onCustomAccentColorChange(color.toArgb())
                        // A typed hex is a seed, never a preset: applying one drops out of the named
                        // theme, which is the escape hatch back to the normal engine.
                        handleColorSelection(color, ThemePreset.NONE)
                    },
                )
            }
        }
    }
}

/**
 * "Colour intensity" — the control that actually answers "why is everything pastel?".
 *
 * Each card previews the CURRENT seed rendered through the real engine
 * ([iad1tya.echo.music.ui.theme.withAccent]), so the difference between the three modes is visible
 * before committing rather than described in prose.
 */
@Composable
private fun AccentIntensitySection(
    seedColor: Color,
    selected: AccentVividness,
    isTvOrCar: Boolean,
    presetActive: Boolean,
    onSelect: (AccentVividness) -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val surface = MaterialTheme.colorScheme.surface
    // ONE scheme generation feeds all three previews.
    val tonalScheme = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = isSystemDark,
        style = PaletteStyle.TonalSpot,
    )

    Column {
        Text(
            text = stringResource(R.string.accent_intensity),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
        Text(
            // With no manual colour picked the scheme comes from the WALLPAPER (Material You) and the
            // seed is only a sentinel, so intensity is a no-op — say so rather than let the user press
            // a control that visibly does nothing.
            text = when {
                // A named theme ships finished, contrast-verified accents; re-tinting them from a
                // seed would undo that, so intensity is deliberately inert here. Say so.
                presetActive -> stringResource(R.string.accent_intensity_fixed_theme)
                seedColor == DefaultThemeColor -> stringResource(R.string.accent_intensity_needs_color)
                else -> stringResource(R.string.accent_intensity_desc)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccentVividness.entries.forEach { mode ->
                AccentIntensityCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(
                        when (mode) {
                            AccentVividness.SOFT -> R.string.accent_intensity_soft
                            AccentVividness.VIVID -> R.string.accent_intensity_vivid
                            AccentVividness.EXACT -> R.string.accent_intensity_exact
                        }
                    ),
                    description = stringResource(
                        when (mode) {
                            AccentVividness.SOFT -> R.string.accent_intensity_soft_desc
                            AccentVividness.VIVID -> R.string.accent_intensity_vivid_desc
                            AccentVividness.EXACT -> R.string.accent_intensity_exact_desc
                        }
                    ),
                    previewColor = remember(tonalScheme, seedColor, mode, surface) {
                        tonalScheme.withAccent(seedColor, mode, surface).primary
                    },
                    isSelected = selected == mode,
                    isTvOrCar = isTvOrCar,
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

@Composable
private fun AccentIntensityCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    previewColor: Color,
    isSelected: Boolean,
    isTvOrCar: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                },
                shape = shape,
            )
            .tvFocusable(isTvOrCar, shape, addFocusable = true, scaleFocused = 1f)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(previewColor)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Manual hex entry. Parsing is live, so the swatch previews what the user typed before they commit,
 * and malformed input only ever produces an inline error — [ColorPickerConversions.parseHexColor]
 * cannot throw. The sample glyph on the swatch is drawn with
 * [iad1tya.echo.music.ui.theme.onColorFor], the same luminance rule the theme itself will use, so a
 * near-black or near-white entry visibly demonstrates its own contrast before being applied.
 */
@Composable
private fun CustomAccentSection(
    selectedThemeColor: Color,
    customAccentColorInt: Int,
    isTvOrCar: Boolean,
    onApply: (Color) -> Unit,
) {
    val startingColor = if (customAccentColorInt != 0) Color(customAccentColorInt) else selectedThemeColor
    var hexInput by rememberSaveable { mutableStateOf(ColorPickerConversions.colorToHex(startingColor)) }

    val parsed = ColorPickerConversions.parseHexColor(hexInput)
    val showError = hexInput.isNotBlank() && parsed == null
    val previewColor = parsed ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val applyColor = { parsed?.let(onApply) }
    // Read here: a semantics {} block is not a composable scope, so stringResource cannot run inside it.
    val previewDescription = stringResource(R.string.accent_preview)

    Column {
        Text(
            text = stringResource(R.string.accent_custom_color),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
        Text(
            text = stringResource(R.string.accent_custom_color_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.cardElevation(0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(previewColor)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(20.dp)
                            )
                            .semantics {
                                contentDescription = previewDescription
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onColorFor(previewColor),
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it.take(9) },
                        label = { Text(stringResource(R.string.accent_hex_label)) },
                        placeholder = { Text(stringResource(R.string.accent_hex_placeholder)) },
                        supportingText = if (showError) {
                            { Text(stringResource(R.string.accent_hex_invalid)) }
                        } else {
                            null
                        },
                        isError = showError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { applyColor() }),
                        modifier = Modifier
                            .weight(1f)
                            // The field is already a focus target; this only draws the D-pad ring so a
                            // TV/car user can SEE that the remote has landed on it.
                            .tvFocusable(isTvOrCar, RoundedCornerShape(8.dp), scaleFocused = 1f),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { applyColor() },
                    enabled = parsed != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusable(isTvOrCar, scaleFocused = 1f),
                ) {
                    Text(stringResource(R.string.accent_hex_apply))
                }
            }
        }
    }
}

@Composable
fun ThemeModeCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scale"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "borderWidth"
    )
    
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }
    
    val backgroundBrush = if (isSelected) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundBrush)
            .border(borderWidth, borderColor, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun PaletteItem(
    palette: ThemePalette,
    isSelected: Boolean,
    onClick: () -> Unit,
    vividness: AccentVividness = AccentVividness.SOFT,
    isTvOrCar: Boolean = false,
) {
    val isSystemDark = isSystemInDarkTheme()
    val surface = MaterialTheme.colorScheme.surface

    val colorScheme = rememberDynamicColorScheme(
        seedColor = palette.seedColor,
        isDark = isSystemDark,
        style = PaletteStyle.TonalSpot
    )

    // The swatch must show what the seed will ACTUALLY look like at the current intensity — under
    // SOFT that is the tonal (pastel) primary, which is exactly why every swatch used to look alike.
    val swatchColor = remember(colorScheme, palette.seedColor, vividness, surface) {
        colorScheme.withAccent(palette.seedColor, vividness, surface).primary
    }

    // A named theme is not a seed, so a tonal swatch would misrepresent it. It previews as the icon's
    // own teal -> blue -> violet gradient. Remembered: a Brush is an allocation, and this composable
    // sits inside a FlowRow that recomposes on every selection change.
    val presetBrush = remember(palette.preset) {
        if (palette.preset == ThemePreset.NONE) null else Brush.linearGradient(MuestreoGradientStops)
    }

    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 56.dp * 0.3f else 28.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cornerRadius"
    )
    
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "borderWidth"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val cardShape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    
    val paletteName = stringResource(palette.nameRes)
    val contentDesc = stringResource(R.string.cd_palette_item, paletteName)
    
    Box(
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isSelected) 8f else 2f
                shape = RoundedCornerShape(cornerRadius)
                clip = true
            }
            .then(
                if (presetBrush != null) {
                    Modifier.background(presetBrush, cardShape)
                } else {
                    Modifier.background(swatchColor, cardShape)
                }
            )
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(
                        width = borderWidth,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = cardShape
                    )
                } else {
                    Modifier
                }
            )
            .tvFocusable(isTvOrCar, cardShape, addFocusable = true, scaleFocused = 1f)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .semantics {
                contentDescription = contentDesc
            }
    ) {
        if (palette.seedColor == Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.palette),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        
        if (isSelected) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    // Computed from the swatch actually drawn, not from the tonal scheme: the deep and
                    // near-white presets would swallow a tonal onPrimary tick.
                    tint = when {
                        palette.seedColor == Color.Transparent -> MaterialTheme.colorScheme.onSurfaceVariant
                        // onColorFor would pick WHITE here (the gradient's mid sits below luminance
                        // 0.5) and white reads 1.55:1 on the teal end. The theme's own onPrimary ink
                        // clears 4.1:1 on the worst stop and 12.4:1 on the teal.
                        presetBrush != null -> MuestreoDarkColorScheme.onPrimary
                        else -> onColorFor(swatchColor)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
