package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import iad1tya.echo.music.notices.OwnerAnnouncements

/**
 * Owner notices never interrupt with a dialog. Unread items are a red dot on the avatar;
 * opening Avisos clears them.
 */
@Composable
fun OwnerNoticePopupHost(
    @Suppress("UNUSED_PARAMETER") enabled: Boolean = true,
) {
}

/** Force a notices pull when this composable enters composition (e.g. shell visible). */
@Composable
fun OwnerNoticesWarmup() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        OwnerAnnouncements.loadCache(context)
        OwnerAnnouncements.refresh(context, force = true)
    }
}
