

package iad1tya.echo.music.ui.utils

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * This used to allocate a NEW [AppBarScrollBehavior] on every recomposition (no `remember`).
 * MainActivity passes the result into the NavHost builder lambda, so an unstable instance
 * invalidated NavHost's internal `remember` and made it re-run `createGraph()` every frame —
 * pure waste on the busiest code path in the app.
 *
 * [canScroll] is wrapped in [rememberUpdatedState] rather than used as a `remember` key: the
 * behavior instance stays stable across recompositions while still calling the LATEST lambda,
 * so a caller whose lambda captures changing values can never read a stale one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarScrollBehavior(
    state: TopAppBarState = rememberTopAppBarState(),
    canScroll: () -> Boolean = { true },
    snapAnimationSpec: AnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
    flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay(),
): TopAppBarScrollBehavior {
    val currentCanScroll by rememberUpdatedState(canScroll)
    return remember(state, snapAnimationSpec, flingAnimationSpec) {
        AppBarScrollBehavior(
            state = state,
            snapAnimationSpec = snapAnimationSpec,
            flingAnimationSpec = flingAnimationSpec,
            canScroll = { currentCanScroll() },
        )
    }
}

@ExperimentalMaterial3Api
class AppBarScrollBehavior(
    override val state: TopAppBarState,
    override val snapAnimationSpec: AnimationSpec<Float>?,
    override val flingAnimationSpec: DecayAnimationSpec<Float>?,
    val canScroll: () -> Boolean = { true },
) : TopAppBarScrollBehavior {
    override val isPinned: Boolean = true
    override var nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero
                state.contentOffset += consumed.y
                if (state.heightOffset == 0f || state.heightOffset == state.heightOffsetLimit) {
                    if (consumed.y == 0f && available.y > 0f) {


                        state.contentOffset = 0f
                    }
                }
                state.heightOffset += consumed.y
                return Offset.Zero
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
suspend fun TopAppBarState.resetHeightOffset() {
    if (heightOffset != 0f) {
        animate(
            initialValue = heightOffset,
            targetValue = 0f,
        ) { value, _ ->
            heightOffset = value
        }
    }
}
