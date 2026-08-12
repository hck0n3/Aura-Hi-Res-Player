package iad1tya.echo.music.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Public support inbox for Aura Hi-Res Player.
 *
 * Users open their own mail app with a prefilled message — nothing is uploaded silently.
 * Keep this address in sync with Terms clause 20 and the Privacy Policy contact section.
 */
object SupportContact {
    const val EMAIL = "aurahires@gmail.com"

    enum class Kind {
        BUG,
        SUGGESTION,
    }

    fun subject(kind: Kind): String = when (kind) {
        Kind.BUG -> "[Aura] Error report"
        Kind.SUGGESTION -> "[Aura] Suggestion"
    }

    /**
     * Opens the system mail composer addressed to [EMAIL].
     * When [attachLogs] is true, shares a text attachment (needs ACTION_SEND).
     * Returns false if no activity can handle the intent.
     */
    fun openFeedback(
        context: Context,
        kind: Kind,
        userMessage: String,
        attachLogs: Boolean,
    ): Boolean {
        val header = DiagnosticHeader.build(context, "feedback")
        val body = buildString {
            appendLine(userMessage.trim().ifBlank { "(no message)" })
            appendLine()
            append(header)
        }
        val subject = subject(kind)

        return if (attachLogs) {
            openWithOptionalLogAttachment(context, subject, body)
        } else {
            openMailto(context, subject, body)
        }
    }

    private fun openMailto(context: Context, subject: String, body: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return startChooser(context, intent)
    }

    private fun openWithOptionalLogAttachment(
        context: Context,
        subject: String,
        body: String,
    ): Boolean {
        val attachment = runCatching {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            val crash = AppLogger.readLastCrash(context)
            val appLog = AppLogger.readRecentLog(context)
            val fileBody = buildString {
                append(body)
                appendLine()
                if (crash.isNotBlank()) {
                    appendLine("--- LAST CRASH ---")
                    appendLine(crash)
                    appendLine()
                }
                if (appLog.isNotBlank()) {
                    appendLine("--- APP LOG (recent) ---")
                    append(appLog)
                }
            }
            File(dir, "aura_feedback.txt").also { it.writeText(fileBody) }
        }.getOrNull()

        if (attachment == null || !attachment.exists()) {
            return openMailto(context, subject, body)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.FileProvider",
            attachment,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(attachment.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startChooser(context, intent)
    }

    private fun startChooser(context: Context, intent: Intent): Boolean {
        return runCatching {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
