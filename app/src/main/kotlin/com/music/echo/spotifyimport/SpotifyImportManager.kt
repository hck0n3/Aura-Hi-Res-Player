package iad1tya.echo.music.spotifyimport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.R
import iad1tya.echo.music.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the Spotify import on a process-lifetime scope so it keeps going after the user leaves the
 * import screen (the screen's ViewModel no longer owns the work). Exposes progress/summary/error as
 * flows for any screen that's open, and posts a system notification with live progress + a final
 * "completed" notification — so the user can keep using the app and is told when it's done.
 */
@Singleton
class SpotifyImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SpotifyImportRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _progress = MutableStateFlow<SpotifyImportProgressUi?>(null)
    val progress = _progress.asStateFlow()

    private val _summary = MutableStateFlow<SpotifyImportSummaryUi?>(null)
    val summary = _summary.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val isRunning: Boolean get() = job?.isActive == true

    fun start(sources: List<SpotifyImportSource>) {
        if (isRunning || sources.isEmpty()) return
        _summary.value = null
        _error.value = null
        ensureChannel()
        job = scope.launch {
            try {
                val result = repository.importSources(sources) { p ->
                    _progress.value = p
                    showOngoing(p)
                }
                _progress.value = null
                _summary.value = result
                showComplete(result)
            } catch (e: CancellationException) {
                _progress.value = null
                cancelNotification()
                throw e
            } catch (e: Throwable) {
                reportException(e)
                _progress.value = null
                _error.value = e.message
                showFailed()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _progress.value = null
        cancelNotification()
    }

    fun consumeSummary() { _summary.value = null }
    fun consumeError() { _error.value = null }

    // --- Notifications -------------------------------------------------------------------------

    private fun manager() = NotificationManagerCompat.from(context)

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.spotify_import_title),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun base(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_nobg)
            .setOnlyAlertOnce(true)

    private fun notify(id: Int, n: Notification) {
        try {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                manager().notify(id, n)
            }
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — ignore.
        }
    }

    private fun showOngoing(p: SpotifyImportProgressUi) {
        val n = base()
            .setContentTitle(context.getString(R.string.spotify_import_title))
            .setContentText("${p.sourceTitle} • ${p.completedSources}/${p.totalSources}")
            .setProgress(100, p.percent, false)
            .setOngoing(true)
            .build()
        notify(NOTIF_ID, n)
    }

    private fun showComplete(s: SpotifyImportSummaryUi) {
        val n = base()
            .setContentTitle(context.getString(R.string.spotify_import_done_title))
            .setContentText(
                context.getString(
                    R.string.spotify_import_done_text,
                    s.importedTracks,
                    s.totalTracks,
                )
            )
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notify(NOTIF_ID, n)
    }

    private fun showFailed() {
        val n = base()
            .setContentTitle(context.getString(R.string.spotify_import_failed_title))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notify(NOTIF_ID, n)
    }

    private fun cancelNotification() {
        try { manager().cancel(NOTIF_ID) } catch (_: Exception) {}
    }

    companion object {
        private const val CHANNEL_ID = "spotify_import"
        private const val NOTIF_ID = 70713
    }
}
