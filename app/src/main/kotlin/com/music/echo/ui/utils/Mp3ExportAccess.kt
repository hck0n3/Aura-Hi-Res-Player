package iad1tya.echo.music.ui.utils

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import iad1tya.echo.music.R

/**
 * First-time MP3 export: if no folder is saved yet, open the system folder picker (Storage Access
 * Framework) — that IS the storage-access grant. On API ≤28 also request WRITE_EXTERNAL_STORAGE
 * before the picker when missing.
 *
 * Returns a lambda: call with the export kickoff; it either runs immediately with the saved URI
 * or opens the picker and then runs with the newly granted URI.
 */
@Composable
fun rememberMp3ExportFolderAccess(
    exportDirectoryUri: String,
    onExportDirectoryUriChange: (String) -> Unit,
): (startExport: (directoryUri: String) -> Unit) -> Unit {
    val context = LocalContext.current
    var pendingStart by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val start = pendingStart
        pendingStart = null
        if (uri == null || start == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val uriStr = uri.toString()
        onExportDirectoryUriChange(uriStr)
        start(uriStr)
    }

    val openFolderPicker: () -> Unit = {
        try {
            folderLauncher.launch(null)
        } catch (_: ActivityNotFoundException) {
            pendingStart = null
            Toast.makeText(
                context,
                context.getString(R.string.export_directory_picker_unavailable),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            openFolderPicker()
        } else {
            pendingStart = null
            Toast.makeText(
                context,
                context.getString(R.string.export_directory_picker_unavailable),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    return remember(exportDirectoryUri, onExportDirectoryUriChange) {
        { startExport ->
            if (exportDirectoryUri.isNotBlank()) {
                startExport(exportDirectoryUri)
            } else {
                pendingStart = startExport
                val needLegacyWrite = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                if (needLegacyWrite && context is Activity) {
                    writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    openFolderPicker()
                }
            }
        }
    }
}
