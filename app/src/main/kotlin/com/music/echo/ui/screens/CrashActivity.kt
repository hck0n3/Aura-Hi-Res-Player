

package iad1tya.echo.music.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.theme.echomusicTheme
import iad1tya.echo.music.ui.theme.rememberNewUiForcesDarkTheme
import iad1tya.echo.music.utils.CrashHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val crashLog = intent.getStringExtra(CrashHandler.EXTRA_CRASH_LOG) ?: getString(R.string.crash_no_log)
        
        setContent {
            // Its own Activity, its own theme: this screen has never read DarkModeKey, so a user on
            // "Oscuro" with a light-mode phone has always got a light crash screen. That is left alone.
            // Only the "Interfaz nueva" term is added — with the redesign on the whole app is forced
            // dark, so a light crash screen is the same white-against-the-redesign mismatch as the rest
            // of this fix. Flag off this is exactly `isSystemInDarkTheme()`.
            //
            // It costs ONE boolean preference read on the crash path (the same cached-seed read every
            // screen does); if DataStore itself were unreadable the app could not have started.
            val darkTheme = rememberNewUiForcesDarkTheme() || isSystemInDarkTheme()
            echomusicTheme(darkTheme = darkTheme) {
                CrashScreen(
                    crashLog = crashLog,
                    onClose = { finishAffinity() },
                    onShare = { shareCrashLog(crashLog) },
                    onCopy = { copyToClipboard(crashLog) }
                )
            }
        }
    }

    private fun copyToClipboard(crashLog: String) {
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("CrashLog", crashLog)
        cm.setPrimaryClip(clip)
        android.widget.Toast.makeText(this, R.string.copied_to_clipboard, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun shareCrashLog(crashLog: String) {
        try {
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "aura_crash_$timestamp.txt"
            val crashFile = File(cacheDir, fileName)
            // Full bundle: crash text plus app log, system exits, and recent playback log so
            // support never gets only the throwable without the surrounding session.
            crashFile.writeText(
                iad1tya.echo.music.utils.AppLogger.buildFullShareBundle(
                    context = this,
                    reason = "crash share",
                    prepend = crashLog,
                )
            )
            
            
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.FileProvider",
                crashFile
            )
            
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, getString(R.string.crash_share_title)))
        } catch (e: Exception) {
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, crashLog)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject))
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.crash_share_title)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(
    crashLog: String,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.crash_title),
                        style = MaterialTheme.typography.headlineSmall
                    ) 
                },
                actions = {
                    IconButton(onClick = onCopy) {
                        Icon(
                            painter = painterResource(R.drawable.content_copy),
                            contentDescription = stringResource(R.string.copy_logs)
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.crash_close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onShare,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.crash_share_logs)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.crash_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(16.dp)
            ) {
                Text(
                    text = crashLog,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
            
            
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}
