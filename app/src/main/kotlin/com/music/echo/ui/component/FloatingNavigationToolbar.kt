

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package iad1tya.echo.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.draw.blur
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.ui.component.GlassComponent
import iad1tya.echo.music.ui.component.LocalGlassEffectConfig
import iad1tya.echo.music.ui.component.glassContentColor
import iad1tya.echo.music.ui.component.isGlassSupported
import iad1tya.echo.music.ui.component.liquidGlass
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    onFabClick: (() -> Unit)? = null,
    fabIconRes: Int? = null,
    fabContentDescription: String = "",
    onShuffleClick: (() -> Unit)? = null,
    shuffleIconRes: Int? = null,
    shuffleContentDescription: String = "",
    onMusicRecognitionClick: (() -> Unit)? = null,
    musicRecognitionContentDescription: String = "",
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
) {
    // Liquid Glass (Beta): when the glass nav-bar surface is active the toolbar container goes
    // transparent and the pill renders as a backdrop-sampling glass surface. Content colors stay
    // theme-adaptive (see floatingToolbar*Color below) so text/icons remain legible in BOTH light
    // and dark themes — never hardcoded white.
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassSupported()
    val toolbarContainerColor = if (useGlass) Color.Transparent else floatingToolbarContainerColor(pureBlack = pureBlack)
    val toolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(
        toolbarContainerColor = toolbarContainerColor,
    )
    val glassModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = RoundedCornerShape(percent = 50),
        )
    } else {
        Modifier
    }
    // Recognition (Shazam-style) is surfaced as a persistent, always-visible mic FAB in the toolbar's
    // trailing action slot — never buried in an overflow menu — so it's one tap away on every main
    // screen. Shuffle, when available, sits beside it as a second inline FAB.
    val hasQuickActions = (onShuffleClick != null && shuffleIconRes != null) || onMusicRecognitionClick != null
    val hasFabAction = onFabClick != null && fabIconRes != null

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val showSelectedLabels = true

        if (hasQuickActions) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarQuickActions(
                        pureBlack = pureBlack,
                        onShuffleClick = onShuffleClick,
                        shuffleIconRes = shuffleIconRes,
                        shuffleContentDescription = shuffleContentDescription,
                        onMusicRecognitionClick = onMusicRecognitionClick,
                        musicRecognitionContentDescription = musicRecognitionContentDescription,
                    )
                },
                modifier = glassModifier.widthIn(max = 480.dp),
                colors = toolbarColors,
                scrollBehavior = scrollBehavior,
                animationSpec = FloatingToolbarDefaults.animationSpec(),
            ) {
                ToolbarItemsContainer(
                    items = items,
                    pureBlack = pureBlack,
                    showSelectedLabels = showSelectedLabels,
                    isSelected = isSelected,
                    onItemClick = onItemClick
                )
            }
        } else if (hasFabAction) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarFabAction(
                        pureBlack = pureBlack,
                        onClick = onFabClick,
                        iconRes = fabIconRes,
                        contentDescription = fabContentDescription,
                    )
                },
                modifier = glassModifier.widthIn(max = 480.dp),
                colors = toolbarColors,
                scrollBehavior = scrollBehavior,
                animationSpec = FloatingToolbarDefaults.animationSpec(),
            ) {
                ToolbarItemsContainer(
                    items = items,
                    pureBlack = pureBlack,
                    showSelectedLabels = showSelectedLabels,
                    isSelected = isSelected,
                    onItemClick = onItemClick
                )
            }
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = glassModifier.widthIn(max = 420.dp),
                colors = toolbarColors,
                scrollBehavior = scrollBehavior,
            ) {
                ToolbarItemsContainer(
                    items = items,
                    pureBlack = pureBlack,
                    showSelectedLabels = showSelectedLabels,
                    isSelected = isSelected,
                    onItemClick = onItemClick
                )
            }
        }
    }
}

@Composable
private fun ToolbarItemsContainer(
    items: List<Screens>,
    pureBlack: Boolean,
    showSelectedLabels: Boolean,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit
) {
    val density = LocalDensity.current
    val itemWidths = remember { mutableStateMapOf<Screens, Dp>() }
    val itemPositions = remember { mutableStateMapOf<Screens, Dp>() }

    val activeScreen = items.find { isSelected(it) }
    val targetWidth = itemWidths[activeScreen] ?: 0.dp
    val targetPosition = itemPositions[activeScreen] ?: 0.dp

    val slidingPillWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillWidth"
    )

    val slidingPillOffset by animateDpAsState(
        targetValue = targetPosition,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillOffset"
    )

    Box(modifier = Modifier.height(IntrinsicSize.Min)) {
        if (targetWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = slidingPillOffset)
                    .width(slidingPillWidth)
                    .fillMaxHeight()
                    .background(
                        color = floatingToolbarSelectedItemContainerColor(pureBlack),
                        shape = RoundedCornerShape(24.dp)
                    )
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            items.forEach { screen ->
                val selected = isSelected(screen)
                FloatingNavigationToolbarItem(
                    screen = screen,
                    selected = selected,
                    showSelectedLabel = showSelectedLabels,
                    pureBlack = pureBlack,
                    onClick = { onItemClick(screen, selected) },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        itemWidths[screen] = with(density) { coordinates.size.width.toDp() }
                        itemPositions[screen] = with(density) { coordinates.positionInParent().x.toDp() }
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingToolbarQuickActions(
    pureBlack: Boolean,
    onShuffleClick: (() -> Unit)?,
    shuffleIconRes: Int?,
    shuffleContentDescription: String,
    onMusicRecognitionClick: (() -> Unit)?,
    musicRecognitionContentDescription: String,
) {
    val isTvOrCar = rememberIsTvOrCar()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onShuffleClick != null && shuffleIconRes != null) {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onShuffleClick,
                modifier = Modifier.tvFocusable(isTvOrCar, CircleShape, scaleFocused = 1f),
                shape = CircleShape,
                containerColor = floatingToolbarFabContainerColor(pureBlack = pureBlack),
                contentColor = floatingToolbarFabContentColor(pureBlack = pureBlack),
            ) {
                Icon(
                    painter = painterResource(shuffleIconRes),
                    contentDescription = shuffleContentDescription.ifEmpty { stringResource(R.string.shuffle) },
                )
            }
        }

        // Persistent, always-visible recognition (Shazam-style) mic button — never hidden in an overflow.
        if (onMusicRecognitionClick != null) {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onMusicRecognitionClick,
                modifier = Modifier.tvFocusable(isTvOrCar, CircleShape, scaleFocused = 1f),
                shape = CircleShape,
                containerColor = floatingToolbarFabContainerColor(pureBlack = pureBlack),
                contentColor = floatingToolbarFabContentColor(pureBlack = pureBlack),
            ) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = musicRecognitionContentDescription.ifEmpty { stringResource(R.string.recognition) },
                )
            }
        }
    }
}

@Composable
private fun FloatingToolbarFabAction(
    pureBlack: Boolean,
    onClick: (() -> Unit)?,
    iconRes: Int?,
    contentDescription: String,
) {
    if (onClick == null || iconRes == null) return

    FloatingToolbarDefaults.VibrantFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.tvFocusable(rememberIsTvOrCar(), CircleShape, scaleFocused = 1f),
        containerColor = floatingToolbarFabContainerColor(pureBlack = pureBlack),
        contentColor = floatingToolbarFabContentColor(pureBlack = pureBlack),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription =
                contentDescription.ifEmpty {
                    stringResource(R.string.create_playlist)
                },
        )
    }
}

@Composable
private fun FloatingNavigationToolbarItem(
    screen: Screens,
    selected: Boolean,
    showSelectedLabel: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val showLabel = selected && showSelectedLabel
    val transition = updateTransition(targetState = selected, label = "navItem_${screen.route}")

    val contentColor by transition.animateColor(
        transitionSpec = { spring(stiffness = Spring.StiffnessMedium) },
        label = "contentColor",
    ) { isSelected ->
        if (isSelected) floatingToolbarSelectedItemContentColor(pureBlack)
        else floatingToolbarItemContentColor(pureBlack)
    }

    val iconScale by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "iconScale",
    ) { isSelected -> if (isSelected) 1.12f else 1.0f }

    val horizontalPadding by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        },
        label = "horizontalPadding",
    ) { isSelected ->
        if (isSelected && showSelectedLabel) 16.dp else 12.dp
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )

    Row(
        modifier = modifier
            .scale(pressScale)
            .tvFocusable(rememberIsTvOrCar(), shape, scaleFocused = 1f)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .widthIn(min = 48.dp)
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(
            targetState = selected,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "iconCrossfade",
            modifier = Modifier.scale(iconScale),
        ) { isSelected ->
            Icon(
                painter = painterResource(if (isSelected) screen.iconIdActive else screen.iconIdInactive),
                contentDescription = stringResource(screen.titleId),
                tint = contentColor,
            )
        }

        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + expandHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                expandFrom = Alignment.Start,
            ),
            exit = fadeOut(
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + shrinkHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                shrinkTowards = Alignment.Start,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(screen.titleId),
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun floatingToolbarContainerColor(pureBlack: Boolean): Color {
    return if (pureBlack) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
}

@Composable
private fun floatingToolbarFabContainerColor(pureBlack: Boolean): Color {
    // Use the theme's primary accent (not tertiary, which is a different hue that clashed with the theme).
    return if (pureBlack) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun floatingToolbarFabContentColor(pureBlack: Boolean): Color {
    return if (pureBlack) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
private fun floatingToolbarSelectedItemContainerColor(pureBlack: Boolean): Color {
    val glassConfig = LocalGlassEffectConfig.current
    if (glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassSupported()) {
        // Subtle adaptive pill on glass: derived from the adaptive content color so it reads
        // in both themes instead of assuming a dark backdrop.
        return glassContentColor(glassConfig).copy(alpha = 0.14f)
    }
    return if (pureBlack) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun floatingToolbarSelectedItemContentColor(pureBlack: Boolean): Color {
    val glassConfig = LocalGlassEffectConfig.current
    if (glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassSupported()) {
        return glassContentColor(glassConfig)
    }
    return if (pureBlack) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
}


@Composable
private fun floatingToolbarItemContentColor(pureBlack: Boolean): Color {
    val glassConfig = LocalGlassEffectConfig.current
    if (glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassSupported()) {
        return glassContentColor(glassConfig).copy(alpha = 0.65f)
    }
    return if (pureBlack) {
        Color.White.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun floatingToolbarMenuIconContainerColor(pureBlack: Boolean): Color {
    return if (pureBlack) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun floatingToolbarMenuIconContentColor(pureBlack: Boolean): Color {
    return if (pureBlack) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
}
