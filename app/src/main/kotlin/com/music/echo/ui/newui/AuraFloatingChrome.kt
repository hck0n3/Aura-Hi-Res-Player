package iad1tya.echo.music.ui.newui

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.ui.component.isGlassSupported
import iad1tya.echo.music.utils.DeviceCapabilities
import iad1tya.echo.music.utils.DeviceForm
import iad1tya.echo.music.utils.DeviceTier
import iad1tya.echo.music.utils.rememberPreference

/**
 * Shared floating chrome for New UI overlays (dialogs, sheets, menus).
 *
 * Owner: everything should feel like the same premium shell. Overlays that are not full Aura
 * screens use a **frosted translucent plate** so what is behind shows through — never a flat
 * Material card, never a naked transparent hole.
 *
 * Real translucency comes from [AuraPalette.FrostFill] (true alpha) + a lighter dim. On capable
 * devices, dialog windows also get system backdrop blur (API 31+). Account sheet uses the same
 * frost plate ([SettingDialoge] → [AuraFloatingSurface]).
 */
object AuraFloating {
    val BlurRadiusPx = 48
    val Shape: CornerBasedShape get() = AuraShapes.Card
}

/**
 * When true, list rows inside this overlay use a transparent resting fill so they sit ON the frost
 * plate instead of painting opaque [AuraPalette.Ground] cards on top of it.
 */
val LocalAuraFloatingChrome = staticCompositionLocalOf { false }

@Composable
fun rememberAuraFrostBlurAllowed(): Boolean {
    val (highPerf) = rememberPreference(HighPerformanceModeKey, false)
    val context = LocalContext.current
    return remember(highPerf, context) {
        !highPerf &&
            isGlassSupported() &&
            DeviceCapabilities.tier(context).let { it == DeviceTier.MID || it == DeviceTier.HIGH } &&
            !DeviceForm.isTvOrCar(context)
    }
}

/**
 * Makes a Compose Dialog window see-through and optionally frosted (system blur of whatever is
 * behind the window). No-op when New UI premium path is off.
 */
@Composable
fun AuraDialogWindowEffects(enabled: Boolean) {
    val view = LocalView.current
    val blurOk = rememberAuraFrostBlurAllowed()
    DisposableEffect(enabled, blurOk, view) {
        if (!enabled) return@DisposableEffect onDispose { }
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.setDimAmount(0.38f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurOk) {
                try {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply {
                        blurBehindRadius = AuraFloating.BlurRadiusPx
                    }
                    window.setBackgroundBlurRadius(AuraFloating.BlurRadiusPx)
                } catch (_: Throwable) {
                    // OEM / TV builds may reject blur flags; translucency still applies.
                }
            }
        }
        onDispose { }
    }
}

/**
 * Premium floating plate: translucent frost + hairline + floating-chrome locals for children.
 */
@Composable
fun AuraFloatingSurface(
    modifier: Modifier = Modifier,
    shape: Shape = AuraFloating.Shape,
    content: @Composable BoxScope.() -> Unit,
) {
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    if (!premium) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            content = { Box { content() } },
        )
        return
    }

    Box(modifier = modifier.clip(shape)) {
        Box(
            Modifier
                .matchParentSize()
                .background(AuraPalette.FrostFill, shape),
        )
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, AuraPalette.SurfaceLine),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {}
        CompositionLocalProvider(LocalAuraFloatingChrome provides true) {
            Box(content = content)
        }
    }
}

@Composable
fun auraFloatingContainerColor(): Color {
    val skin = rememberAuraPanelSkin()
    return if (skin.enabled && skin.darkGround) AuraPalette.FrostFill
    else MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
fun auraFloatingContentColor(): Color {
    val skin = rememberAuraPanelSkin()
    return if (skin.enabled && skin.darkGround) AuraPalette.OnGround
    else MaterialTheme.colorScheme.onSurface
}

@Composable
fun auraFloatingScrimColor(): Color {
    val skin = rememberAuraPanelSkin()
    // Lighter than Material's default so the frosted plate can still reveal the UI behind.
    return if (skin.enabled && skin.darkGround) Color.Black.copy(alpha = 0.38f)
    else MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
}

@Composable
fun auraFloatingTextFieldColors() =
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AuraPalette.Teal,
        unfocusedBorderColor = AuraPalette.SurfaceLine,
        focusedContainerColor = AuraPalette.SurfaceFill,
        unfocusedContainerColor = AuraPalette.SurfaceFill,
        cursorColor = AuraPalette.Teal,
        focusedTextColor = AuraPalette.OnGround,
        unfocusedTextColor = AuraPalette.OnGround,
        focusedPlaceholderColor = AuraPalette.OnGroundMuted,
        unfocusedPlaceholderColor = AuraPalette.OnGroundMuted,
        focusedLeadingIconColor = AuraPalette.Teal,
        unfocusedLeadingIconColor = AuraPalette.OnGroundMuted,
        focusedTrailingIconColor = AuraPalette.OnGroundMuted,
        unfocusedTrailingIconColor = AuraPalette.OnGroundMuted,
    )
