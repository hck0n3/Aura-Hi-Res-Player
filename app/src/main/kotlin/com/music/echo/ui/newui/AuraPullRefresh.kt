package iad1tya.echo.music.ui.newui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.LocalPlayerAwareWindowInsets

/**
 * Premium pull-to-refresh: a teal hairline that grows with the gesture and pulses while loading.
 * Replaces the floating Material3 circular spinner the owner disliked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.AuraPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val pull = state.distanceFraction.coerceIn(0f, 1f)
    if (!isRefreshing && pull <= 0.02f) return
    val pulse = if (isRefreshing) {
        val infinite = rememberInfiniteTransition(label = "aura-ptr")
        val value by infinite.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "aura-ptr-pulse",
        )
        value
    } else {
        0.45f + 0.55f * pull
    }
    val widthFraction = if (isRefreshing) 1f else 0.22f + 0.78f * pull
    Box(
        modifier = modifier
            .align(Alignment.TopCenter)
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(2.dp)
            .padding(horizontal = AuraSpacing.Gutter),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(widthFraction)
                .clip(AuraShapes.Pill)
                .background(AuraPalette.Teal.copy(alpha = pulse)),
        )
    }
}
