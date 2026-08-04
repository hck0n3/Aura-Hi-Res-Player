@file:OptIn(ExperimentalMaterial3Api::class)

package iad1tya.echo.music.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.AppLogger
import iad1tya.echo.music.utils.DiagnosticHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Which log the screen is showing. [SYSTEM_EXITS] is Android's own record of why previous processes
 * died (low memory / ANR / native crash): those kills never reach CrashHandler, so they are absent
 * from both the app log and the last-crash report and need their own tab.
 */
private enum class LogTab { APP, CRASH, SYSTEM_EXITS }

@Composable
fun LogsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(LogTab.APP) }
    var reloadTrigger by remember { mutableStateOf(0) }

    val logText by produceState(initialValue = "", tab, reloadTrigger) {
        value = withContext(Dispatchers.IO) {
            val body = when (tab) {
                LogTab.APP -> AppLogger.readRecentLog(context)
                LogTab.CRASH -> AppLogger.readLastCrash(context)
                LogTab.SYSTEM_EXITS -> AppLogger.readExitReasons(context)
            }
            // Prepend the diagnostic header HERE, not in each action, so the on-screen text, the copy
            // button and the share button are the same bytes — the user sees exactly what they send,
            // and neither path can be the one that forgot it. Built on IO with the body, never on the
            // main thread. `exit_reasons.txt` in particular carried no header at all before: a
            // LOW_MEMORY kill arrived with no way to tell which build or which device produced it.
            if (body.isBlank()) "" else DiagnosticHeader.build(context, "shared log") + "\n" + body
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.logs),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { copyToClipboard(context, logText) }, onLongClick = {}) {
                        Icon(painterResource(R.drawable.content_copy), contentDescription = stringResource(R.string.copy))
                    }
                    IconButton(onClick = { shareLog(context, tab, logText) }, onLongClick = {}) {
                        Icon(painterResource(R.drawable.share), contentDescription = stringResource(R.string.share))
                    }
                    IconButton(onClick = { AppLogger.clear(context); reloadTrigger++ }, onLongClick = {}) {
                        Icon(painterResource(R.drawable.delete), contentDescription = stringResource(R.string.delete))
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Horizontally scrollable: three chips with long Spanish labels overflow a narrow screen.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = tab == LogTab.APP,
                    onClick = { tab = LogTab.APP },
                    label = { Text(stringResource(R.string.app_log)) },
                )
                FilterChip(
                    selected = tab == LogTab.CRASH,
                    onClick = { tab = LogTab.CRASH },
                    label = { Text(stringResource(R.string.last_crash)) },
                )
                FilterChip(
                    selected = tab == LogTab.SYSTEM_EXITS,
                    onClick = { tab = LogTab.SYSTEM_EXITS },
                    label = { Text(stringResource(R.string.system_exits)) },
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                if (logText.isBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(
                            text = stringResource(R.string.no_logs_yet),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = logText,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Aura Hi-Res Player logs", text))
}

private fun shareLog(context: Context, tab: LogTab, text: String) {
    if (text.isBlank()) return
    runCatching {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        val shareFile = File(
            dir,
            when (tab) {
                LogTab.APP -> "share_log.txt"
                LogTab.CRASH -> "share_crash.txt"
                LogTab.SYSTEM_EXITS -> "share_exit_reasons.txt"
            },
        )
        shareFile.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", shareFile)
        // Share the .txt as a FILE attachment. Including EXTRA_TEXT made most apps paste the whole log
        // as a message instead of attaching the document, so only the stream (file) is provided.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, shareFile.name)
            clipData = android.content.ClipData.newRawUri(shareFile.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
