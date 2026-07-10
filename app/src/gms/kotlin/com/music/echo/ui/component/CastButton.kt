

package iad1tya.echo.music.ui.component

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnableGoogleCastKey
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.rememberPreference
import timber.log.Timber

/**
 * A Composable Cast button that shows available Cast devices.
 * Uses the app's MenuState to show a styled bottom sheet.
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    
    var castAvailable by remember { mutableStateOf(false) }
    var mediaRouter by remember { mutableStateOf<MediaRouter?>(null) }
    var routeSelector by remember { mutableStateOf<MediaRouteSelector?>(null) }
    var availableRoutes by remember { mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList()) }
    
    val (enableGoogleCast) = rememberPreference(
        key = EnableGoogleCastKey,
        defaultValue = true
    )
    
    // Get cast state from service
    val castHandler = playerConnection?.service?.castConnectionHandler
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val isConnecting by castHandler?.isConnecting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castDeviceName by castHandler?.castDeviceName?.collectAsState() ?: remember { mutableStateOf(null) }
    
    // Get current media metadata
    val currentMetadata by playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }

    // Check if Cast is available and disconnect if disabled while casting
    LaunchedEffect(enableGoogleCast) {
        if (!enableGoogleCast) {
            // Disconnect from Cast if currently casting
            if (isCasting) {
                playerConnection?.service?.castConnectionHandler?.disconnect()
            }
            castAvailable = false
            mediaRouter = null
            routeSelector = null
            availableRoutes = emptyList()
            return@LaunchedEffect
        }
        try {
            CastContext.getSharedInstance(context)
            mediaRouter = MediaRouter.getInstance(context)
            routeSelector = MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build()
            // Reinitialize the Cast handler to ensure it's ready
            playerConnection?.service?.castConnectionHandler?.initialize()
            castAvailable = true
        } catch (e: Exception) {
            Timber.d("Cast not available: ${e.message}")
            castAvailable = false
        }
    }
    
    // NO MediaRouter callback while the picker is closed: ACTIVE discovery (Wi-Fi/mDNS scanning) is
    // expensive — per Google Cast guidance it must never run for the whole expanded-player lifetime
    // (multi-hour sessions → radio/battery drain). The button itself renders from CastContext
    // availability + the cast handler's connection state alone; the route list is snapshotted on
    // click and then kept live by a discovery callback registered ONLY while the CastPickerSheet is
    // open (see the DisposableEffect inside menuState.show below).

    // Show the button if Cast is enabled and SDK is available
    if (enableGoogleCast && castAvailable) {
        Box(
            modifier = modifier
        ) {
            // Shadow background for cast button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Cast button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    // TV/car: visible D-pad focus ring observing the .clickable below (same pattern as the
                    // player's transport buttons) — the button is pinned top-right of the expanded player.
                    .tvFocusable(iad1tya.echo.music.ui.utils.rememberIsTvOrCar(), RoundedCornerShape(20.dp))
                    .clickable {
                    if (currentMetadata == null && !isCasting) {
                        Toast.makeText(context, "Play a song first to cast", Toast.LENGTH_SHORT).show()
                        return@clickable
                    }

                    // Refresh the route snapshot before showing the sheet (cheap — reads MediaRouter's
                    // current list, no scan). Active discovery then fills it in while the sheet is open.
                    routeSelector?.let { selector ->
                        updateRoutes(mediaRouter, selector) { availableRoutes = it }
                    }

                    // Get current connected route if casting
                    val currentRoute = if (isCasting) {
                        mediaRouter?.routes?.find { route ->
                            routeSelector?.let { selector ->
                                route.matchesSelector(selector) && route.isSelected
                            } == true
                        }
                    } else null

                    // Show bottom sheet with cast picker
                    menuState.show {
                        // ACTIVE discovery scoped to the sheet: registering here starts scanning when
                        // the picker composes and onDispose stops it as soon as the sheet is dismissed
                        // (any path — route picked, disconnect, scrim tap, replaced by another menu).
                        DisposableEffect(Unit) {
                            val router = mediaRouter
                            val selector = routeSelector
                            val callback = object : MediaRouter.Callback() {
                                override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) {
                                    updateRoutes(r, selector) { availableRoutes = it }
                                }

                                override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) {
                                    updateRoutes(r, selector) { availableRoutes = it }
                                }

                                override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) {
                                    updateRoutes(r, selector) { availableRoutes = it }
                                }
                            }
                            if (router != null && selector != null) {
                                router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
                                updateRoutes(router, selector) { availableRoutes = it }
                            }
                            onDispose { router?.removeCallback(callback) }
                        }
                        CastPickerSheet(
                            routes = availableRoutes,
                            isConnecting = isConnecting,
                            currentlyConnectedRoute = currentRoute,
                            onRouteSelected = { route ->
                                castHandler?.connectToRoute(route)
                                menuState.dismiss()
                            },
                            onDisconnect = {
                                castHandler?.disconnect()
                                menuState.dismiss()
                            }
                        )
                    }
                }
            ) {
                Image(
                    painter = painterResource(
                        if (isCasting) R.drawable.cast_connected else R.drawable.cast
                    ),
                    contentDescription = if (isCasting) "Stop casting" else "Cast",
                    colorFilter = ColorFilter.tint(
                        if (isCasting) MaterialTheme.colorScheme.primary else tintColor
                    ),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun updateRoutes(
    router: MediaRouter?,
    selector: MediaRouteSelector?,
    onUpdate: (List<MediaRouter.RouteInfo>) -> Unit
) {
    if (router == null || selector == null) {
        onUpdate(emptyList())
        return
    }
    val routes = router.routes.filter { route ->
        route.matchesSelector(selector) && !route.isDefault
    }
    onUpdate(routes)
}
