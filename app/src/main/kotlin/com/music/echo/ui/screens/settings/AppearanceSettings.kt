

package iad1tya.echo.music.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.border
import androidx.core.content.edit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.ui.component.isGlassEligible
import iad1tya.echo.music.ui.newui.auraScreenBackground
import iad1tya.echo.music.ui.newui.rememberAuraBloom
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AlbumCanvasEnabledKey
import iad1tya.echo.music.constants.CanvasThumbnailAnimationKey
import iad1tya.echo.music.constants.ChipSortTypeKey
import iad1tya.echo.music.constants.CropAlbumArtKey
import iad1tya.echo.music.constants.DefaultOpenTabKey
import iad1tya.echo.music.constants.DensityScale
import iad1tya.echo.music.constants.DensityScaleKey
import iad1tya.echo.music.constants.DynamicThemeKey
import iad1tya.echo.music.constants.EnableHighRefreshRateKey
import iad1tya.echo.music.constants.EnableLyricsThumbnailPlayPauseKey
import iad1tya.echo.music.constants.GridItemSize
import iad1tya.echo.music.constants.GridItemsSizeKey
import iad1tya.echo.music.constants.HidePlayerThumbnailKey
import iad1tya.echo.music.constants.LibraryFilter
import iad1tya.echo.music.constants.ListenTogetherInTopBarKey
import iad1tya.echo.music.constants.LyricsAnimationStyle
import iad1tya.echo.music.constants.LyricsAnimationStyleKey
import iad1tya.echo.music.constants.LyricsStandardBlurKey
import iad1tya.echo.music.constants.LyricsTextPositionKey
import iad1tya.echo.music.constants.LyricsTextSizeKey
import iad1tya.echo.music.constants.PlayerBackgroundStyle
import iad1tya.echo.music.constants.PlayerBackgroundStyleKey
import iad1tya.echo.music.constants.PlayerButtonsStyle
import iad1tya.echo.music.constants.PlayerButtonsStyleKey

import iad1tya.echo.music.constants.RotatingThumbnailKey
import iad1tya.echo.music.constants.SelectedThemeColorKey
import iad1tya.echo.music.constants.ShowCachedPlaylistKey
import iad1tya.echo.music.constants.ShowExportedPlaylistKey
import iad1tya.echo.music.constants.ShowExportedVideosPlaylistKey
import iad1tya.echo.music.constants.ShowDownloadedPlaylistKey
import iad1tya.echo.music.constants.ShowLikedPlaylistKey
import iad1tya.echo.music.constants.ShowTopPlaylistKey
import iad1tya.echo.music.constants.SliderStyle
import iad1tya.echo.music.constants.SliderStyleKey
import iad1tya.echo.music.constants.SquigglySliderKey
import iad1tya.echo.music.constants.SwipeSensitivityKey
import iad1tya.echo.music.constants.SwipeThumbnailKey
import iad1tya.echo.music.constants.SwipeLyricsKey
import iad1tya.echo.music.constants.SwipeToRemoveSongKey
import iad1tya.echo.music.constants.SwipeToSongKey
import iad1tya.echo.music.constants.ThumbnailCornerRadiusKey

import iad1tya.echo.music.constants.UseNewPlayerDesignKey
import iad1tya.echo.music.ui.component.ThumbnailCornerRadiusModal
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.EnumDialog
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.ui.component.PlayerSliderTrack
import iad1tya.echo.music.ui.component.SquigglySlider
import iad1tya.echo.music.ui.component.WavySlider
import iad1tya.echo.music.ui.theme.DefaultThemeColor
import iad1tya.echo.music.ui.theme.PlayerSliderColors
import iad1tya.echo.music.ui.theme.rememberNewUiForcesDarkTheme
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import kotlin.math.roundToInt
import iad1tya.echo.music.constants.LyricsClickKey
import iad1tya.echo.music.constants.AppleMusicLyricsBlurKey
import iad1tya.echo.music.constants.LyricsGlowEffectKey
import iad1tya.echo.music.constants.LyricsLineSpacingKey
import iad1tya.echo.music.constants.LyricsScrollKey
import iad1tya.echo.music.constants.HideStatusBarOnFullscreenKey
import iad1tya.echo.music.constants.MiniPlayerBackgroundStyleKey
import iad1tya.echo.music.constants.ShowCommentButtonKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    activity: Activity,
) {
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(
        DynamicThemeKey,
        defaultValue = true
    )
    val (globalHaptics, onGlobalHapticsChange) = rememberPreference(
        iad1tya.echo.music.constants.GlobalHapticsKey,
        defaultValue = true
    )
    val (enableHighRefreshRate, onEnableHighRefreshRateChange) = rememberPreference(
        iad1tya.echo.music.constants.EnableHighRefreshRateKey,
        defaultValue = true
    )
    val (selectedThemeColorInt) = rememberPreference(
        SelectedThemeColorKey,
        defaultValue = DefaultThemeColor.toArgb()
    )

    val isUsingCustomColor = selectedThemeColorInt != DefaultThemeColor.toArgb()

    val (useNewPlayerDesign, onUseNewPlayerDesignChange) = rememberPreference(
        UseNewPlayerDesignKey,
        defaultValue = true
    )
    // READ-ONLY. Every row of this screen is reachable with the "Interfaz nueva" flag on (the new Ajustes
    // index navigates to this very screen), so a row whose scope narrows under the new UI has to say so.
    // Nothing here writes the flag — reading it cannot make the beta less reversible.
    val newUiEnabled = iad1tya.echo.music.ui.newui.rememberNewUiEnabled()
    val (showCodecOnPlayer, onShowCodecOnPlayerChange) = rememberPreference(
        iad1tya.echo.music.constants.ShowCodecOnPlayerKey,
        defaultValue = false
    )
    val (hidePlayerSlider, onHidePlayerSliderChange) = rememberPreference(
        iad1tya.echo.music.constants.HidePlayerSliderKey,
        defaultValue = false
    )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) = rememberPreference(
        HidePlayerThumbnailKey,
        defaultValue = false
    )
    val (cropAlbumArt, onCropAlbumArtChange) = rememberPreference(
        CropAlbumArtKey,
        defaultValue = false
    )
    val (playerBackground, onPlayerBackgroundChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.GRADIENT,
        )
    val (miniPlayerBackground, onMiniPlayerBackgroundChange) =
        rememberEnumPreference(
            MiniPlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.GLOW_ANIMATED,
        )

    val (defaultOpenTab, onDefaultOpenTabChange) = rememberEnumPreference(
        DefaultOpenTabKey,
        defaultValue = NavigationTab.HOME
    )
    val (playerButtonsStyle, onPlayerButtonsStyleChange) = rememberEnumPreference(
        PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )
    val (lyricsPosition, onLyricsPositionChange) = rememberEnumPreference(
        LyricsTextPositionKey,
        defaultValue = LyricsPosition.LEFT
    )
    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll, onLyricsScrollChange) = rememberPreference(
        LyricsScrollKey,
        defaultValue = true
    )
    val (lyricsAnimationStyle, onLyricsAnimationStyleChange) = rememberEnumPreference(
        LyricsAnimationStyleKey,
        defaultValue = LyricsAnimationStyle.echomusic_1
    )
    val (lyricsTextSize, onLyricsTextSizeChange) = rememberPreference(LyricsTextSizeKey, defaultValue = 24f)
    val (lyricsLineSpacing, onLyricsLineSpacingChange) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (lyricsGlowEffect, onLyricsGlowEffectChange) = rememberPreference(LyricsGlowEffectKey, defaultValue = false)
    val (appleMusicLyricsBlur, onAppleMusicLyricsBlurChange) = rememberPreference(AppleMusicLyricsBlurKey, defaultValue = true)
    val (lyricsStandardBlur, onLyricsStandardBlurChange) = rememberPreference(LyricsStandardBlurKey, defaultValue = false)
    val (swipeLyrics, onSwipeLyricsChange) = rememberPreference(SwipeLyricsKey, defaultValue = false)
    val (enableLyricsThumbnailPlayPause, onEnableLyricsThumbnailPlayPauseChange) = rememberPreference(EnableLyricsThumbnailPlayPauseKey, defaultValue = false)
    val (hideStatusBarOnFullscreen, onHideStatusBarOnFullscreenChange) = rememberPreference(HideStatusBarOnFullscreenKey, defaultValue = true)

    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(
        SliderStyleKey,
        defaultValue = SliderStyle.DEFAULT
    )
    val (squigglySlider, onSquigglySliderChange) = rememberPreference(
        SquigglySliderKey,
        defaultValue = false
    )
    val (swipeThumbnail, onSwipeThumbnailChange) = rememberPreference(
        SwipeThumbnailKey,
        defaultValue = true
    )
    val (swipeSensitivity, onSwipeSensitivityChange) = rememberPreference(
        SwipeSensitivityKey,
        defaultValue = 0.73f
    )
    val (canvasThumbnailAnimation, onCanvasThumbnailAnimationChange) = rememberPreference(
        CanvasThumbnailAnimationKey,
        defaultValue = false
    )
    val (rotatingThumbnail, onRotatingThumbnailChange) = rememberPreference(
        RotatingThumbnailKey,
        defaultValue = false
    )
    // Moved here from ContentSettings (IA consolidation — player-UI canvas lives in Appearance). Key unchanged.
    val (albumCanvasEnabled, onAlbumCanvasEnabledChange) = rememberPreference(
        AlbumCanvasEnabledKey,
        defaultValue = false
    )
    val (gridItemSize, onGridItemSizeChange) = rememberEnumPreference(
        GridItemsSizeKey,
        defaultValue = GridItemSize.SMALL
    )

    
    val context = activity as Context
    val sharedPreferences = remember { context.getSharedPreferences("echomusic_settings", Context.MODE_PRIVATE) }
    val prefDensityScale = remember(sharedPreferences) {
        sharedPreferences.getFloat("density_scale_factor", 1.0f)
    }
    val (densityScale, setDensityScale) = rememberPreference(DensityScaleKey, defaultValue = prefDensityScale)
    var showRestartDialog by rememberSaveable { mutableStateOf(false) }
    var showDensityScaleDialog by rememberSaveable { mutableStateOf(false) }

    val onDensityScaleChange: (Float) -> Unit = { newScale ->
        setDensityScale(newScale)
        
        sharedPreferences.edit {
            putFloat("density_scale_factor", newScale)
        }
        showRestartDialog = true
    }

    val (listenTogetherInTopBar, onListenTogetherInTopBarChange) = rememberPreference(
        ListenTogetherInTopBarKey,
        defaultValue = true
    )

    val (swipeToSong, onSwipeToSongChange) = rememberPreference(
        SwipeToSongKey,
        defaultValue = false
    )

    val (swipeToRemoveSong, onSwipeToRemoveSongChange) = rememberPreference(
        SwipeToRemoveSongKey,
        defaultValue = false
    )

    val (showLikedPlaylist, onShowLikedPlaylistChange) = rememberPreference(
        ShowLikedPlaylistKey,
        defaultValue = true
    )
    val (showDownloadedPlaylist, onShowDownloadedPlaylistChange) = rememberPreference(
        ShowDownloadedPlaylistKey,
        defaultValue = true
    )
    val (showExportedPlaylist, onShowExportedPlaylistChange) = rememberPreference(
        ShowExportedPlaylistKey,
        defaultValue = true
    )
    val (showExportedVideosPlaylist, onShowExportedVideosPlaylistChange) = rememberPreference(
        ShowExportedVideosPlaylistKey,
        defaultValue = true
    )
    val (showTopPlaylist, onShowTopPlaylistChange) = rememberPreference(
        ShowTopPlaylistKey,
        defaultValue = true
    )
    val (showCachedPlaylist, onShowCachedPlaylistChange) = rememberPreference(
        ShowCachedPlaylistKey,
        defaultValue = true
    )
    val (showCommentButton, onShowCommentButtonChange) = rememberPreference(
        ShowCommentButtonKey,
        defaultValue = false
    )

    // Liquid Glass only appears as a choice on eligible devices (API 31+, MID/HIGH tier,
    // not TV/car, Performance Mode off); everywhere else the option is hidden.
    val glassEligible = remember { isGlassEligible(activity) }
    val availableBackgroundStyles = PlayerBackgroundStyle.entries.filter {
        (it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) &&
            // [isGlassEligible] gates the SHADER, and under the new interface this value is not the
            // shader: `auraGroundRecipe` draws LIQUID_GLASS as a still, blurred cover under a white
            // film, with a colour-wash fallback when there is no cover to blur (a local track, or
            // API < 31 where `Modifier.blur` is a no-op). Nothing in it samples a backdrop, nothing in
            // it moves, so none of the eligibility gates apply — and keeping them would have gone on
            // withholding a working style from LOW-tier, TV/car and Performance-Mode users for a cost
            // this shape does not pay. With the flag OFF the term vanishes and the list is the classic
            // one, value for value.
            (it != PlayerBackgroundStyle.LIQUID_GLASS || glassEligible || newUiEnabled)
    }

    val availableMiniPlayerBackgroundStyles = availableBackgroundStyles.filter { 
        it != PlayerBackgroundStyle.APPLE_MUSIC && it != PlayerBackgroundStyle.GRADIENT
    }



    val (defaultChip, onDefaultChipChange) = rememberEnumPreference(
        key = ChipSortTypeKey,
        defaultValue = LibraryFilter.LIBRARY
    )

    var showSliderOptionDialog by rememberSaveable {
        mutableStateOf(false)
    }



    var showPlayerBackgroundDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showMiniPlayerBackgroundDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showPlayerButtonsStyleDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showLyricsPositionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showLyricsAnimationStyleDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showLyricsTextSizeDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showLyricsLineSpacingDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showLyricsPositionDialog) {
        EnumDialog(
            onDismiss = { showLyricsPositionDialog = false },
            onSelect = {
                onLyricsPositionChange(it)
                showLyricsPositionDialog = false
            },
            title = stringResource(R.string.lyrics_text_position),
            current = lyricsPosition,
            values = LyricsPosition.values().toList(),
            valueText = {
                when (it) {
                    LyricsPosition.LEFT -> stringResource(R.string.left)
                    LyricsPosition.CENTER -> stringResource(R.string.center)
                    LyricsPosition.RIGHT -> stringResource(R.string.right)
                }
            }
        )
    }

    if (showLyricsAnimationStyleDialog) {
        EnumDialog(
            onDismiss = { showLyricsAnimationStyleDialog = false },
            onSelect = {
                onLyricsAnimationStyleChange(it)
                showLyricsAnimationStyleDialog = false
            },
            title = stringResource(R.string.lyrics_animation_style),
            current = lyricsAnimationStyle,
            values = LyricsAnimationStyle.values().toList(),
            valueText = {
                when (it) {
                    LyricsAnimationStyle.NONE -> stringResource(R.string.none)
                    LyricsAnimationStyle.FADE -> stringResource(R.string.fade)
                    LyricsAnimationStyle.GLOW -> stringResource(R.string.glow)
                    LyricsAnimationStyle.SLIDE -> stringResource(R.string.slide)
                    LyricsAnimationStyle.KARAOKE -> stringResource(R.string.karaoke)
                    LyricsAnimationStyle.APPLE -> stringResource(R.string.apple_music_style)
                    LyricsAnimationStyle.APPLE_V2 -> stringResource(R.string.apple_music_style_letter)
                    LyricsAnimationStyle.echomusic_1 -> stringResource(R.string.echomusic_1)
                    LyricsAnimationStyle.LYRICS_V2 -> stringResource(R.string.lyrics_v2_fluid)
                    LyricsAnimationStyle.METRO_LYRICS -> stringResource(R.string.lyrics_animation_metro)
                }
            }
        )
    }

    if (showLyricsTextSizeDialog) {
        var tempTextSize by remember { mutableFloatStateOf(lyricsTextSize) }
        
        DefaultDialog(
            onDismiss = { 
                tempTextSize = lyricsTextSize
                showLyricsTextSizeDialog = false 
            },
            buttons = {
                TextButton(
                    onClick = { 
                        tempTextSize = 24f
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                TextButton(
                    onClick = { 
                        tempTextSize = lyricsTextSize
                        showLyricsTextSizeDialog = false 
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = { 
                        onLyricsTextSizeChange(tempTextSize)
                        showLyricsTextSizeDialog = false 
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.lyrics_text_size),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "${tempTextSize.roundToInt()} sp",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Slider(
                    value = tempTextSize,
                    onValueChange = { tempTextSize = it },
                    valueRange = 16f..36f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showLyricsLineSpacingDialog) {
        var tempLineSpacing by remember { mutableFloatStateOf(lyricsLineSpacing) }
        
        DefaultDialog(
            onDismiss = { 
                tempLineSpacing = lyricsLineSpacing
                showLyricsLineSpacingDialog = false 
            },
            buttons = {
                TextButton(
                    onClick = { 
                        tempLineSpacing = 1.3f
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                TextButton(
                    onClick = { 
                        tempLineSpacing = lyricsLineSpacing
                        showLyricsLineSpacingDialog = false 
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = { 
                        onLyricsLineSpacingChange(tempLineSpacing)
                        showLyricsLineSpacingDialog = false 
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.lyrics_line_spacing),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "${String.format("%.1f", tempLineSpacing)}x",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Slider(
                    value = tempLineSpacing,
                    onValueChange = { tempLineSpacing = it },
                    valueRange = 1.0f..4.0f,
                    steps = 59,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showPlayerButtonsStyleDialog) {
        EnumDialog(
            onDismiss = { showPlayerButtonsStyleDialog = false },
            onSelect = {
                onPlayerButtonsStyleChange(it)
                showPlayerButtonsStyleDialog = false
            },
            title = stringResource(R.string.player_buttons_style),
            current = playerButtonsStyle,
            values = PlayerButtonsStyle.values().toList(),
            valueText = {
                when (it) {
                    PlayerButtonsStyle.DEFAULT -> stringResource(R.string.default_style)
                    PlayerButtonsStyle.PRIMARY -> stringResource(R.string.primary_color_style)
                    PlayerButtonsStyle.TERTIARY -> stringResource(R.string.tertiary_color_style)
                }
            }
        )
    }

    if (showPlayerBackgroundDialog) {
        EnumDialog(
            onDismiss = { showPlayerBackgroundDialog = false },
            onSelect = {
                onPlayerBackgroundChange(it)
                showPlayerBackgroundDialog = false
            },
            title = stringResource(R.string.player_background_style),
            current = playerBackground,
            values = availableBackgroundStyles,
            valueText = {
                when (it) {
                    PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                    PlayerBackgroundStyle.GLOW_ANIMATED -> stringResource(R.string.glow_animated)
                    PlayerBackgroundStyle.APPLE_MUSIC -> stringResource(R.string.apple_music)
                    PlayerBackgroundStyle.LIVE_MESH -> stringResource(R.string.live_mesh)
                    PlayerBackgroundStyle.LIQUID_GLASS -> stringResource(R.string.player_background_liquid_glass)
                }
            }
        )
    }

    if (showMiniPlayerBackgroundDialog) {
        EnumDialog(
            onDismiss = { showMiniPlayerBackgroundDialog = false },
            onSelect = {
                onMiniPlayerBackgroundChange(it)
                showMiniPlayerBackgroundDialog = false
            },
            title = stringResource(R.string.miniplayer_background_style),
            current = miniPlayerBackground,
            values = availableMiniPlayerBackgroundStyles,
            valueText = {
                when (it) {
                    // GRADIENT and APPLE_MUSIC are filtered OUT of availableMiniPlayerBackgroundStyles
                    // above, so neither can ever be shown here and neither gets a branch.
                    PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                    PlayerBackgroundStyle.GLOW_ANIMATED -> stringResource(R.string.glow_animated)
                    PlayerBackgroundStyle.LIVE_MESH -> stringResource(R.string.live_mesh)
                    PlayerBackgroundStyle.LIQUID_GLASS -> stringResource(R.string.player_background_liquid_glass)
                    // Unreachable safety net only (the `when` must stay exhaustive).
                    else -> "Unknown"
                }
            }
        )
    }


    var showDefaultOpenTabDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showDefaultOpenTabDialog) {
        EnumDialog(
            onDismiss = { showDefaultOpenTabDialog = false },
            onSelect = {
                onDefaultOpenTabChange(it)
                showDefaultOpenTabDialog = false
            },
            title = stringResource(R.string.default_open_tab),
            current = defaultOpenTab,
            values = NavigationTab.values().toList(),
            valueText = {
                when (it) {
                    NavigationTab.HOME -> stringResource(R.string.home)
                    NavigationTab.SEARCH -> stringResource(R.string.search)
                    NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                }
            }
        )
    }

    var showDefaultChipDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showDefaultChipDialog) {
        EnumDialog(
            onDismiss = { showDefaultChipDialog = false },
            onSelect = {
                onDefaultChipChange(it)
                showDefaultChipDialog = false
            },
            title = stringResource(R.string.default_lib_chips),
            current = defaultChip,
            values = LibraryFilter.values().toList(),
            valueText = {
                when (it) {
                    LibraryFilter.SONGS -> stringResource(R.string.songs)
                    LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                    LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                    LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                    LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                    LibraryFilter.LOCAL -> stringResource(R.string.filter_local)
                }
            }
        )
    }

    var showGridSizeDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showGridSizeDialog) {
        EnumDialog(
            onDismiss = { showGridSizeDialog = false },
            onSelect = {
                onGridItemSizeChange(it)
                showGridSizeDialog = false
            },
            title = stringResource(R.string.grid_cell_size),
            current = gridItemSize,
            values = GridItemSize.values().toList(),
            valueText = {
                when (it) {
                    GridItemSize.BIG -> stringResource(R.string.big)
                    GridItemSize.SMALL -> stringResource(R.string.small)
                }
            }
        )
    }

    if (showRestartDialog) {
        DefaultDialog(
            onDismiss = { showRestartDialog = false },
            buttons = {
                TextButton(
                    onClick = { showRestartDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                ) {
                    Text(text = stringResource(R.string.restart))
                }
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.restart_required),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.density_restart_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (showDensityScaleDialog) {
        DefaultDialog(
            onDismiss = { showDensityScaleDialog = false },
            buttons = {
                TextButton(
                    onClick = { showDensityScaleDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        ) {
            Column {
                DensityScale.entries.forEach { scale ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDensityScaleChange(scale.value)
                                showDensityScaleDialog = false
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = scale.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (densityScale == scale.value) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSliderOptionDialog) {
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showSliderOptionDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showSliderOptionDialog = false
            }
        ) {
            // The slider PREVIEW has to be drawn in the colours the app is actually wearing. This site
            // never read DarkModeKey — it asks the SYSTEM only — so it keeps that, plus the "Interfaz
            // nueva" term: with the redesign on the app is dark and a light-mode phone previewed the
            // light slider over a dark dialog.
            val sliderPreviewColors = PlayerSliderColors.getSliderColors(
                MaterialTheme.colorScheme.primary,
                PlayerBackgroundStyle.DEFAULT,
                rememberNewUiForcesDarkTheme() || isSystemInDarkTheme()
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (sliderStyle == SliderStyle.DEFAULT && !squigglySlider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onSliderStyleChange(SliderStyle.DEFAULT)
                                onSquigglySliderChange(false)
                                showSliderOptionDialog = false
                            }
                            .padding(12.dp)
                    ) {
                        val sliderValue = 0.35f
                        Slider(
                            value = sliderValue,
                            valueRange = 0f..1f,
                                    
                            onValueChange = {  },
                            colors = sliderPreviewColors,
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.default_),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (sliderStyle == SliderStyle.WAVY && !squigglySlider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onSliderStyleChange(SliderStyle.WAVY)
                                onSquigglySliderChange(false)
                                showSliderOptionDialog = false
                            }
                            .padding(12.dp)
                    ) {
                        val sliderValue = 0.5f
                        WavySlider(
                            value = sliderValue,
                            valueRange = 0f..1f,
                                    
                            onValueChange = {  },
                            colors = sliderPreviewColors,
                            modifier = Modifier.weight(1f),
                            isPlaying = true,
                            enabled = false
                        )
                        Text(
                            text = stringResource(R.string.wavy),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (sliderStyle == SliderStyle.SLIM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onSliderStyleChange(SliderStyle.SLIM)
                                onSquigglySliderChange(false)
                                showSliderOptionDialog = false
                            }
                            .padding(12.dp)
                    ) {
                        val sliderValue = 0.65f
                        Slider(
                            value = sliderValue,
                            valueRange = 0f..1f,
                                    
                            onValueChange = {  },
                            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                            track = { sliderState ->
                                PlayerSliderTrack(
                                    sliderState = sliderState,
                                    colors = sliderPreviewColors
                                )
                            },
                            colors = sliderPreviewColors,
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = stringResource(R.string.slim),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (sliderStyle == SliderStyle.WAVY && squigglySlider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onSliderStyleChange(SliderStyle.WAVY)
                                onSquigglySliderChange(true)
                                showSliderOptionDialog = false
                            }
                            .padding(12.dp)
                    ) {
                        val sliderValue = 0.5f
                        SquigglySlider(
                            value = sliderValue,
                            valueRange = 0f..1f,
                                    
                            onValueChange = {  },
                            modifier = Modifier.weight(1f),
                            enabled = false,
                            colors = sliderPreviewColors,
                            isPlaying = true,
                        )
                        Text(
                            text = stringResource(R.string.squiggly),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // With "Interfaz nueva" on, paint the same Aura ground the Ajustes index uses so Tema / Apariencia
    // personalization sits on the redesign instead of a flat Material page.
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val bloom = rememberAuraBloom(mediaMetadata?.id)
    val contentModifier = Modifier
        .then(
            if (newUiEnabled) Modifier.fillMaxSize().auraScreenBackground(bloom, intensity = 0.32f)
            else Modifier,
        )
        .windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            )
        )
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp)

    Column(contentModifier) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )
        Material3SettingsGroup(
            title = stringResource(R.string.theme),
            items = buildList {






















                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.palette),
                        title = { Text(stringResource(R.string.theme)) },
                        description = { Text(stringResource(R.string.theme_desc)) },
                        onClick = { navController.navigate("settings/appearance/theme") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.speed),
                        title = { Text(stringResource(R.string.enable_high_refresh_rate)) },
                        description = { Text(stringResource(R.string.enable_high_refresh_rate_desc)) },
                        trailingContent = {
                            Switch(
                                checked = enableHighRefreshRate,
                                onCheckedChange = onEnableHighRefreshRateChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (enableHighRefreshRate) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onEnableHighRefreshRateChange(!enableHighRefreshRate) }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.vibration),
                        title = { Text(stringResource(R.string.global_haptics)) },
                        description = { Text(stringResource(R.string.global_haptics_desc)) },
                        trailingContent = {
                            Switch(
                                checked = globalHaptics,
                                onCheckedChange = onGlobalHapticsChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (globalHaptics) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onGlobalHapticsChange(!globalHaptics) }
                    )
                )

                
                if (!isUsingCustomColor) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.palette),
                            title = { Text(stringResource(R.string.enable_dynamic_theme)) },
                            trailingContent = {
                                Switch(
                                    checked = dynamicTheme,
                                    onCheckedChange = onDynamicThemeChange,
                                    thumbContent = {
                                        Icon(
                                            painter = painterResource(
                                                id = if (dynamicTheme) R.drawable.check else R.drawable.close
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    }
                                )
                            },
                            onClick = { onDynamicThemeChange(!dynamicTheme) }
                        )
                    )
                }

            }
        )

        // "Mini reproductor" → "Estilo de fondo". LIVE IN BOTH INTERFACES.
        //
        // It used to be hidden with the new UI on, and the comment here defended that: the only
        // renderer of MiniPlayerBackgroundStyleKey was the classic `NewMiniPlayer`, which the redesign
        // never composes, so the row had no consumer. The premise was true and the conclusion was
        // wrong — "en ajustes no se ven las mismas funciones que antes de personalización, NO QUIERO
        // PERDER NADA". Hiding a control is losing it. The fix is a renderer, not a hidden row.
        //
        // `AuraMiniPlayer` (ui/newui/AuraShell.kt) now reads this key, exactly as the classic mini
        // does and with the same default, and paints the pill's ground from it through the same
        // `auraGroundRecipe` the player and queue use. The pill and the player sheet are therefore two
        // separately dressable surfaces again, as they are in the classic app — and a user who wants
        // them to match sets both to the same value, which is a choice rather than a constraint.
        //
        // What it can NOT do, said once here and once on screen: this key's LIQUID_GLASS is a frosted,
        // still cover in the new interface, not the backdrop-sampling shader the classic mini draws.
        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(id = R.string.mini_player),
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.palette),
                        title = { Text(stringResource(R.string.miniplayer_background_style)) },
                        description = {
                            Column {
                                Text(
                                    when (miniPlayerBackground) {
                                        PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
                                        PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                                        PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                                        PlayerBackgroundStyle.GLOW_ANIMATED -> stringResource(R.string.glow_animated)
                                        PlayerBackgroundStyle.LIVE_MESH -> stringResource(R.string.live_mesh)
                                        PlayerBackgroundStyle.LIQUID_GLASS -> stringResource(R.string.player_background_liquid_glass)
                                        else -> stringResource(R.string.follow_theme)
                                    }
                                )
                                if (newUiEnabled) {
                                    Text(stringResource(R.string.miniplayer_background_style_desc_new_ui))
                                }
                            }
                        },
                        onClick = { showMiniPlayerBackgroundDialog = true }
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(27.dp))

        val (thumbnailCornerRadius, onThumbnailCornerRadiusChange) = rememberPreference(
            ThumbnailCornerRadiusKey,
            defaultValue = 3f
        )
        
        var showSensitivityDialog by rememberSaveable { mutableStateOf(false) }
        var showThumbnailCornerRadiusDialog by rememberSaveable { mutableStateOf(false) }

        Material3SettingsGroup(
            title = stringResource(R.string.player),
            items = listOfNotNull(
                // "Inspirado en Apple Music" (UseNewPlayerDesignKey). With the new UI on this ONLY
                // restyles the cover (Thumbnail.kt appleMusicCoverStyle) — never buttons/controls
                // (owner rule). It also still decides WHICH classic player comes back when the beta
                // is switched off. The APPLE_MUSIC background side-write stays classic-only so the
                // row does not half-mutate the ground style under the redesign.
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.apple_music_inspired)) },
                    description = {
                        Text(
                            stringResource(
                                if (newUiEnabled) R.string.apple_music_inspired_desc_new_ui
                                else R.string.apple_music_inspired_desc
                            )
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = !useNewPlayerDesign,
                            onCheckedChange = { isChecked ->
                                onUseNewPlayerDesignChange(!isChecked)
                                if (isChecked && !newUiEnabled) {
                                    onPlayerBackgroundChange(PlayerBackgroundStyle.APPLE_MUSIC)
                                }
                            },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (!useNewPlayerDesign) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = {
                        val newAppleMusicInspired = useNewPlayerDesign
                        onUseNewPlayerDesignChange(!newAppleMusicInspired)
                        if (newAppleMusicInspired && !newUiEnabled) {
                            onPlayerBackgroundChange(PlayerBackgroundStyle.APPLE_MUSIC)
                        }
                    }
                ),
                // "Ocultar control de volumen" (HidePlayerSliderKey — legacy key name; it has never
                // hidden the timeline). Now honoured by BOTH players: the classic Apple-Music transport
                // hides its inline volume row, the new player hides the volume slider at the head of its
                // merged player menu. Labels come from string resources instead of hardcoded literals.
                if (!useNewPlayerDesign) Material3SettingsItem(
                    icon = painterResource(R.drawable.linear_scale),
                    title = { Text(stringResource(R.string.hide_player_volume)) },
                    description = { Text(stringResource(R.string.hide_player_volume_desc)) },
                    trailingContent = {
                        Switch(
                            checked = hidePlayerSlider,
                            onCheckedChange = onHidePlayerSliderChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (hidePlayerSlider) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onHidePlayerSliderChange(!hidePlayerSlider) }
                ) else null,
                // "Fondo del reproductor" (PlayerBackgroundStyleKey), SEVEN values. LIVE EVERYWHERE.
                //
                // It used to be live only outside the new portrait player, and this comment used to
                // explain why. That was wrong and the subtitle said so out loud: APPLE_MUSIC is the value
                // App.kt:604 SEEDS on every fresh install, so the shipped default of this very control was
                // one the new player did not honour, and five more did nothing beside it.
                //
                // All seven now paint the GROUND of the new player and its queue sheet
                // (ui/newui/AuraPlayer.kt, `rememberAuraGround`). NOT the mini pill: that follows
                // MiniPlayerBackgroundStyleKey, its own control, restored in the group above — this key
                // briefly drove both surfaces and that is precisely how the separate setting went
                // missing. Nothing above the ground layer moves, which is
                // what keeps the redesign's own contrast: the artwork colours come from the ONE palette
                // pass the ambient bloom already runs per track, the blurred cover is the same 128×128
                // decode the mini player has shipped for months under an API-31 guard, and the two moving
                // styles read their phase inside a draw lambda and stop dead under Performance Mode,
                // thermal throttling or a LOW hardware tier — the same gate the classic player uses.
                //
                // With the new interface on, this key is honoured by the redesign itself: the seven
                // styles paint the GROUND of the player, its queue sheet and the mini pill
                // (AuraPlayer.kt, rememberAuraGround), while everything above that layer stays Aura's.
                // One setting drives every shape in both interfaces.
                Material3SettingsItem(
                    icon = painterResource(R.drawable.gradient),
                    title = { Text(stringResource(R.string.player_background_style)) },
                    description = {
                        Column {
                            Text(
                                when (playerBackground) {
                                    PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
                                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                                    PlayerBackgroundStyle.GLOW_ANIMATED -> stringResource(R.string.glow_animated)
                                    PlayerBackgroundStyle.APPLE_MUSIC -> stringResource(R.string.apple_music)
                                    PlayerBackgroundStyle.LIVE_MESH -> stringResource(R.string.live_mesh)
                                    PlayerBackgroundStyle.LIQUID_GLASS -> stringResource(R.string.player_background_liquid_glass)
                                }
                            )
                            if (newUiEnabled) {
                                Text(stringResource(R.string.player_background_style_desc_new_ui))
                            }
                        }
                    },
                    onClick = {
                        showPlayerBackgroundDialog = true
                    }
                ),
                // Liquid Glass (Beta): entry point to the glass-effect settings. On ineligible
                // devices (API < 31, LOW tier, TV/car or Performance Mode ON) it is shown
                // disabled with an "unavailable on this device" subtitle instead of hidden,
                // so users know why the option is missing.
                //
                // VISIBLE IN BOTH INTERFACES. It used to be hidden with the new UI on, and that was
                // the third thing the owner noticed missing. It is back, but honestly: with the new
                // interface on the row is SHOWN DISABLED with a line saying what it configures and
                // where the frosted look lives instead. That is the pattern this screen's own Tema
                // sibling already uses (ThemeScreen greys the Claro/Sistema cards and explains why),
                // and it is the difference between "the user learns the truth by walking there" and
                // "the user finds a gap and assumes we broke it".
                //
                // Why disabled rather than live — the measurement, not a preference:
                //  · The glass system has exactly TWO render sites in the app. `FloatingNavigationToolbar`
                //    is composed only on the `!newUiShell` branch (MainActivity.kt) — the new shell draws
                //    `AuraNavigationBar`, and landscape uses the rail, so there is no orientation that
                //    brings it back. The classic `NewMiniPlayer` is replaced by `AuraMiniPlayer` in every
                //    orientation. The full-screen player was NEVER a render site in either interface:
                //    nothing calls `isEnabledFor(GlassComponent.PLAYER)` (GlassEffect.kt:75-79).
                //  · Both of those sites are surfaces that are on screen for the WHOLE of every song, and
                //    `Modifier.liquidGlass` is not a still image: it forces the entire NavHost to be
                //    re-recorded into an offscreen layer (`Modifier.layerBackdrop`, MainActivity.kt) and
                //    then runs a RenderEffect chain — saturation, blur, lens refraction with chromatic
                //    aberration — over that sample on every frame that draws. That is a live full-screen
                //    backdrop sample during playback, which is exactly and only what the redesign's
                //    thermal contract rules out (AuraBloom.kt, rule 2). Re-pointing it at Aura's pills
                //    would not make it cheaper; it is the same shader on the same always-on surfaces.
                //
                // So the SHADER is classic-only for a structural reason, and the row says so. The LOOK
                // is not lost: "Fondo del reproductor" → "Liquid Glass" paints a real frosted ground
                // (still blurred cover + film + hairline, one decode per track, API-31 guarded) on the
                // player, the queue and — now that the mini-player row above is live again — the pill.
                //
                // Nothing is written or cleared in either interface: turn the beta off and the row, the
                // screen and every stored value are back exactly as they were.
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.liquid_glass)) },
                    description = {
                        Text(
                            stringResource(
                                when {
                                    // Ineligibility wins, in BOTH interfaces. Ordering this the other
                                    // way told an API-30 / LOW-tier / TV user that the shader "applies
                                    // to the classic interface" — an invitation to go and turn the beta
                                    // off for something that would not work there either.
                                    !glassEligible -> R.string.liquid_glass_unavailable
                                    newUiEnabled -> R.string.liquid_glass_classic_only_desc
                                    else -> R.string.liquid_glass_settings_desc
                                }
                            )
                        )
                    },
                    enabled = glassEligible && !newUiEnabled,
                    // The row is disabled, and the sentence above is the whole reason it is shown
                    // disabled rather than hidden — so it is the one thing that must NOT inherit the
                    // 38 % alpha a disabled row dims its content to (3.24:1 on the redesign's ground,
                    // where body text needs 4.5:1). Undimmed it composites to 5.67:1; the title, the
                    // icon and the chevron stay dimmed, so the row still reads as not-tappable.
                    //
                    // Gated on the flag, not passed as a constant `true`: with "Interfaz nueva" OFF
                    // this is `false` and the ineligible-device row keeps exactly the classic
                    // appearance it has today.
                    keepDescriptionLegible = newUiEnabled,
                    onClick = {
                        if (glassEligible && !newUiEnabled) {
                            navController.navigate(LIQUID_GLASS_ROUTE)
                        }
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.hide_image),
                    title = { Text(stringResource(R.string.hide_player_thumbnail)) },
                    description = { Text(stringResource(R.string.hide_player_thumbnail_desc)) },
                    trailingContent = {
                        Switch(
                            checked = hidePlayerThumbnail,
                            onCheckedChange = onHidePlayerThumbnailChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (hidePlayerThumbnail) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onHidePlayerThumbnailChange(!hidePlayerThumbnail) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.image),
                    title = { Text(stringResource(R.string.thumbnail_corner_radius)) },
                    description = { Text(stringResource(R.string.thumbnail_corner_radius_desc)) },
                    trailingContent = {
                        Text(
                            text = "${thumbnailCornerRadius.roundToInt()}dp",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showThumbnailCornerRadiusDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.crop),
                    title = { Text(stringResource(R.string.crop_album_art)) },
                    description = { Text(stringResource(R.string.crop_album_art_desc)) },
                    trailingContent = {
                        Switch(
                            checked = cropAlbumArt,
                            onCheckedChange = onCropAlbumArtChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (cropAlbumArt) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onCropAlbumArtChange(!cropAlbumArt) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.player_buttons_style)) },
                    description = {
                        Text(
                            when (playerButtonsStyle) {
                                PlayerButtonsStyle.DEFAULT -> stringResource(R.string.default_style)
                                PlayerButtonsStyle.PRIMARY -> stringResource(R.string.primary_color_style)
                                PlayerButtonsStyle.TERTIARY -> stringResource(R.string.tertiary_color_style)
                            }
                        )
                    },
                    onClick = { showPlayerButtonsStyleDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.sliders),
                    title = { Text(stringResource(R.string.player_slider_style)) },
                    description = {
                        Text(
                            when (sliderStyle) {
                                SliderStyle.DEFAULT -> stringResource(R.string.default_)
                                SliderStyle.WAVY -> if (squigglySlider) stringResource(R.string.squiggly) else stringResource(
                                    R.string.wavy
                                )
                                SliderStyle.SLIM -> stringResource(R.string.slim)
                            }
                        )
                    },
                    onClick = { showSliderOptionDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.swipe),
                    title = { Text(stringResource(R.string.enable_swipe_thumbnail)) },
                    trailingContent = {
                        Switch(
                            checked = swipeThumbnail,
                            onCheckedChange = onSwipeThumbnailChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (swipeThumbnail) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSwipeThumbnailChange(!swipeThumbnail) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.echomusic_canvas)) },
                    description = { Text(stringResource(R.string.echomusic_canvas_desc)) },
                    trailingContent = {
                        Switch(
                            checked = canvasThumbnailAnimation,
                            onCheckedChange = onCanvasThumbnailAnimationChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (canvasThumbnailAnimation) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onCanvasThumbnailAnimationChange(!canvasThumbnailAnimation) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.image),
                    title = { Text(stringResource(R.string.rotating_thumbnail)) },
                    description = { Text(stringResource(R.string.rotating_thumbnail_desc)) },
                    trailingContent = {
                        Switch(
                            checked = rotatingThumbnail,
                            onCheckedChange = onRotatingThumbnailChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (rotatingThumbnail) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onRotatingThumbnailChange(!rotatingThumbnail) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.slow_motion_video),
                    title = { Text(stringResource(R.string.show_album_canvas)) },
                    description = { Text(stringResource(R.string.show_album_canvas_desc)) },
                    trailingContent = {
                        Switch(
                            checked = albumCanvasEnabled,
                            onCheckedChange = onAlbumCanvasEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (albumCanvasEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAlbumCanvasEnabledChange(!albumCanvasEnabled) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.chat_msg),
                    title = { Text(stringResource(R.string.show_comment_button)) },
                    description = { Text(stringResource(R.string.show_comment_button_description)) },
                    trailingContent = {
                        Switch(
                            checked = showCommentButton,
                            onCheckedChange = onShowCommentButtonChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showCommentButton) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowCommentButtonChange(!showCommentButton) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text("Mostrar códec en el reproductor") },
                    description = { Text("Muestra la información del códec de audio debajo de la línea de tiempo") },
                    trailingContent = {
                        Switch(
                            checked = showCodecOnPlayer,
                            onCheckedChange = onShowCodecOnPlayerChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showCodecOnPlayer) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    // Without an onClick the whole row is inert (Material3SettingsItemRow only enables the
                    // click when onClick != null), so only the switch itself reacted — unlike every
                    // neighbouring row.
                    onClick = { onShowCodecOnPlayerChange(!showCodecOnPlayer) }
                ),
                if (swipeThumbnail) Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.swipe_sensitivity)) },
                    description = {
                        Text(
                            stringResource(
                                R.string.sensitivity_percentage,
                                (swipeSensitivity * 100).roundToInt()
                            )
                        )
                    },
                    onClick = { showSensitivityDialog = true }
                ) else null
            )
        )

        if (showSensitivityDialog) {
            var tempSensitivity by remember { mutableFloatStateOf(swipeSensitivity) }

            DefaultDialog(
                onDismiss = {
                    tempSensitivity = swipeSensitivity
                    showSensitivityDialog = false
                },
                buttons = {
                    TextButton(
                        onClick = {
                            tempSensitivity = 0.73f
                        }
                    ) {
                        Text(stringResource(R.string.reset))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            tempSensitivity = swipeSensitivity
                            showSensitivityDialog = false
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            onSwipeSensitivityChange(tempSensitivity)
                            showSensitivityDialog = false
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.swipe_sensitivity),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = stringResource(
                            R.string.sensitivity_percentage,
                            (tempSensitivity * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Slider(
                        value = tempSensitivity,
                        onValueChange = { tempSensitivity = it },
                        valueRange = 0f..1f,
                                    
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (showThumbnailCornerRadiusDialog) {
            ThumbnailCornerRadiusModal(
                initialRadius = thumbnailCornerRadius,
                onDismiss = { showThumbnailCornerRadiusDialog = false },
                onRadiusSelected = { radius ->
                    onThumbnailCornerRadiusChange(radius)
                    showThumbnailCornerRadiusDialog = false
                }
            )
        }

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.lyrics),
            items = listOfNotNull(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_text_position)) },
                    description = {
                        Text(
                            when (lyricsPosition) {
                                LyricsPosition.LEFT -> stringResource(R.string.left)
                                LyricsPosition.CENTER -> stringResource(R.string.center)
                                LyricsPosition.RIGHT -> stringResource(R.string.right)
                            }
                        )
                    },
                    onClick = { showLyricsPositionDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_animation_style)) },
                    description = {
                        Text(
                            when (lyricsAnimationStyle) {
                                LyricsAnimationStyle.NONE -> stringResource(R.string.none)
                                LyricsAnimationStyle.FADE -> stringResource(R.string.fade)
                                LyricsAnimationStyle.GLOW -> stringResource(R.string.glow)
                                LyricsAnimationStyle.SLIDE -> stringResource(R.string.slide)
                                LyricsAnimationStyle.KARAOKE -> stringResource(R.string.karaoke)
                                LyricsAnimationStyle.echomusic_1 -> stringResource(R.string.echomusic_1)
                                LyricsAnimationStyle.APPLE -> stringResource(R.string.apple_music_style)
                                LyricsAnimationStyle.APPLE_V2 -> stringResource(R.string.apple_music_style_letter)
                                LyricsAnimationStyle.LYRICS_V2 -> stringResource(R.string.lyrics_v2_fluid)
                                LyricsAnimationStyle.METRO_LYRICS -> stringResource(R.string.lyrics_animation_metro)
                            }
                        )
                    },
                    onClick = { showLyricsAnimationStyleDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_glow_effect)) },
                    description = { Text(stringResource(R.string.lyrics_glow_effect_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsGlowEffect,
                            onCheckedChange = onLyricsGlowEffectChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsGlowEffect) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsGlowEffectChange(!lyricsGlowEffect) }
                ),
                if (lyricsAnimationStyle == LyricsAnimationStyle.echomusic_1) {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.lyrics),
                        title = { Text(stringResource(R.string.apple_music_lyrics_blur)) },
                        description = { Text(stringResource(R.string.apple_music_lyrics_blur_desc)) },
                        trailingContent = {
                            Switch(
                                checked = appleMusicLyricsBlur,
                                onCheckedChange = onAppleMusicLyricsBlurChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (appleMusicLyricsBlur) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onAppleMusicLyricsBlurChange(!appleMusicLyricsBlur) }
                    )
                } else null,
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.standard_lyrics_blur)) },
                    description = { Text(stringResource(R.string.apple_music_lyrics_blur_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsStandardBlur,
                            onCheckedChange = onLyricsStandardBlurChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsStandardBlur) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsStandardBlurChange(!lyricsStandardBlur) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_text_size)) },
                    description = { Text("${lyricsTextSize.roundToInt()} sp") },
                    onClick = { showLyricsTextSizeDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_line_spacing)) },
                    description = { Text("${String.format("%.1f", lyricsLineSpacing)}x") },
                    onClick = { showLyricsLineSpacingDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_click_change)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsClick,
                            onCheckedChange = onLyricsClickChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsClick) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsClickChange(!lyricsClick) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_auto_scroll)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsScroll,
                            onCheckedChange = onLyricsScrollChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsScroll) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsScrollChange(!lyricsScroll) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.swipe),
                    title = { Text(stringResource(R.string.lyrics_swipe_to_change_song)) },
                    description = { Text(stringResource(R.string.lyrics_swipe_to_change_song_desc)) },
                    trailingContent = {
                        Switch(
                            checked = swipeLyrics,
                            onCheckedChange = onSwipeLyricsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (swipeLyrics) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSwipeLyricsChange(!swipeLyrics) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.play),
                    title = { Text(stringResource(R.string.lyrics_thumbnail_play_pause)) },
                    description = { Text(stringResource(R.string.lyrics_thumbnail_play_pause_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableLyricsThumbnailPlayPause,
                            onCheckedChange = onEnableLyricsThumbnailPlayPauseChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableLyricsThumbnailPlayPause) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableLyricsThumbnailPlayPauseChange(!enableLyricsThumbnailPlayPause) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.fullscreen),
                    title = { Text(stringResource(R.string.hide_status_bar_on_fullscreen)) },
                    description = { Text(stringResource(R.string.hide_status_bar_on_fullscreen_desc)) },
                    trailingContent = {
                        Switch(
                            checked = hideStatusBarOnFullscreen,
                            onCheckedChange = onHideStatusBarOnFullscreenChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (hideStatusBarOnFullscreen) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onHideStatusBarOnFullscreenChange(!hideStatusBarOnFullscreen) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.misc),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.nav_bar),
                    title = { Text(stringResource(R.string.default_open_tab)) },
                    description = {
                        Text(
                            when (defaultOpenTab) {
                                NavigationTab.HOME -> stringResource(R.string.home)
                                NavigationTab.SEARCH -> stringResource(R.string.search)
                                NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                            }
                        )
                    },
                    onClick = { showDefaultOpenTabDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tab),
                    title = { Text(stringResource(R.string.default_lib_chips)) },
                    description = {
                        Text(
                            when (defaultChip) {
                                LibraryFilter.SONGS -> stringResource(R.string.songs)
                                LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                                LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                                LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                                LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                                LibraryFilter.LOCAL -> stringResource(R.string.filter_local)
                            }
                        )
                    },
                    onClick = { showDefaultChipDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.swipe),
                    title = { Text(stringResource(R.string.swipe_song_to_add)) },
                    trailingContent = {
                        Switch(
                            checked = swipeToSong,
                            onCheckedChange = onSwipeToSongChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (swipeToSong) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSwipeToSongChange(!swipeToSong) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.swipe),
                    title = { Text(stringResource(R.string.swipe_song_to_remove)) },
                    trailingContent = {
                        Switch(
                            checked = swipeToRemoveSong,
                            onCheckedChange = onSwipeToRemoveSongChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (swipeToRemoveSong) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSwipeToRemoveSongChange(!swipeToRemoveSong) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.group_outlined),
                    title = { Text(stringResource(R.string.listen_together_in_top_bar)) },
                    description = { Text(stringResource(R.string.listen_together_in_top_bar_desc)) },
                    trailingContent = {
                        Switch(
                            checked = listenTogetherInTopBar,
                            onCheckedChange = onListenTogetherInTopBarChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (listenTogetherInTopBar) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onListenTogetherInTopBarChange(!listenTogetherInTopBar) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.grid_view),
                    title = { Text(stringResource(R.string.grid_cell_size)) },
                    description = {
                        Text(
                            when (gridItemSize) {
                                GridItemSize.BIG -> stringResource(R.string.big)
                                GridItemSize.SMALL -> stringResource(R.string.small)
                            }
                        )
                    },
                    onClick = { showGridSizeDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.grid_view),
                    title = { Text(stringResource(R.string.display_density)) },
                    description = {
                        Text(DensityScale.fromValue(densityScale).label)
                    },
                    onClick = { showDensityScaleDialog = true }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.auto_playlists),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.favorite),
                    title = { Text(stringResource(R.string.show_liked_playlist)) },
                    trailingContent = {
                        Switch(
                            checked = showLikedPlaylist,
                            onCheckedChange = onShowLikedPlaylistChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showLikedPlaylist) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowLikedPlaylistChange(!showLikedPlaylist) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.offline),
                    title = { Text(stringResource(R.string.show_downloaded_playlist)) },
                    trailingContent = {
                        Switch(
                            checked = showDownloadedPlaylist,
                            onCheckedChange = onShowDownloadedPlaylistChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showDownloadedPlaylist) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowDownloadedPlaylistChange(!showDownloadedPlaylist) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.download),
                    title = { Text(stringResource(R.string.action_exported)) },
                    trailingContent = {
                        Switch(
                            checked = showExportedPlaylist,
                            onCheckedChange = onShowExportedPlaylistChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showExportedPlaylist) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowExportedPlaylistChange(!showExportedPlaylist) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.videocam),
                    title = { Text(stringResource(R.string.show_exported_videos_playlist)) },
                    trailingContent = {
                        Switch(
                            checked = showExportedVideosPlaylist,
                            onCheckedChange = onShowExportedVideosPlaylistChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showExportedVideosPlaylist) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowExportedVideosPlaylistChange(!showExportedVideosPlaylist) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.trending_up),
                    title = { Text(stringResource(R.string.show_top_playlist)) },
                    trailingContent = {
                        Switch(
                            checked = showTopPlaylist,
                            onCheckedChange = onShowTopPlaylistChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showTopPlaylist) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowTopPlaylistChange(!showTopPlaylist) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.cached),
                    title = { Text(stringResource(R.string.show_cached_playlist)) },
                    trailingContent = {
                        Switch(
                            checked = showCachedPlaylist,
                            onCheckedChange = onShowCachedPlaylistChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showCachedPlaylist) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShowCachedPlaylistChange(!showCachedPlaylist) }
                ),
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.appearance)) },
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
        }
    )
}

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}
