package iad1tya.echo.music.ui.newui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ReadAnnouncementIdsKey
import iad1tya.echo.music.notices.OwnerAnnouncement
import iad1tya.echo.music.notices.OwnerAnnouncements
import iad1tya.echo.music.notices.unreadOwnerNoticeCount
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.launch

/** Unread owner notices for avatar / account-sheet badges. Refreshes cache in the background. */
@Composable
fun rememberUnreadOwnerNoticesCount(): Int {
    val context = LocalContext.current
    val items by OwnerAnnouncements.items.collectAsState()
    val (readIdsCsv) = rememberPreference(ReadAnnouncementIdsKey, "")
    LaunchedEffect(Unit) {
        OwnerAnnouncements.loadCache(context)
        OwnerAnnouncements.refresh(context)
    }
    return remember(items, readIdsCsv) { unreadOwnerNoticeCount(items, readIdsCsv) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraNoticesScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by OwnerAnnouncements.items.collectAsState()
    var readIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        OwnerAnnouncements.loadCache(context)
        readIds = OwnerAnnouncements.readIds(context)
        OwnerAnnouncements.refresh(context, force = true)
        readIds = OwnerAnnouncements.readIds(context)
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraPalette.Ground),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.owner_notices_title),
                    style = AuraType.ScreenTitle,
                    color = AuraPalette.OnGround,
                )
            },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = AuraPalette.OnGround,
                    )
                }
            },
            actions = {
                if (items.any { it.id !in readIds }) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                OwnerAnnouncements.markAllRead(context)
                                readIds = OwnerAnnouncements.readIds(context)
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.owner_notices_mark_all_read),
                            color = AuraPalette.Teal,
                            style = AuraType.RowSubtitle,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraPalette.Ground),
        )

        when {
            loading && items.isEmpty() -> {
                Text(
                    text = stringResource(R.string.please_wait),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                    modifier = Modifier.padding(AuraSpacing.Gutter),
                )
            }
            items.isEmpty() -> {
                Text(
                    text = stringResource(R.string.owner_notices_empty),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                    modifier = Modifier.padding(AuraSpacing.Gutter),
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = AuraSpacing.Gutter,
                        vertical = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { notice ->
                        NoticeCard(
                            notice = notice,
                            unread = notice.id !in readIds,
                            onOpen = {
                                scope.launch {
                                    OwnerAnnouncements.markRead(context, notice.id)
                                    readIds = OwnerAnnouncements.readIds(context)
                                }
                                notice.url?.let { url ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, url.toUri())
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(
    notice: OwnerAnnouncement,
    unread: Boolean,
    onOpen: () -> Unit,
) {
    val warning = notice.priority.equals("warning", ignoreCase = true) ||
        notice.priority.equals("alert", ignoreCase = true)
    AuraFloatingSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (unread) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
                        drawCircle(color = AuraPalette.Teal)
                    }
                }
                Text(
                    text = notice.title,
                    style = AuraType.RowTitle,
                    color = if (warning) AuraPalette.Teal else AuraPalette.OnGround,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (notice.date.isNotBlank()) {
                Text(
                    text = notice.date,
                    style = AuraType.CalloutSubtitle,
                    color = AuraPalette.OnGroundFaint,
                )
            }
            Text(
                text = notice.body,
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
            )
        }
    }
}
