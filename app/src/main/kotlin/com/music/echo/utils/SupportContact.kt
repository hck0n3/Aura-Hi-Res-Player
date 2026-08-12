package iad1tya.echo.music.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Public support inbox for Aura Hi-Res Player.
 *
 * Opens ONLY a mail app (prefers the user's default), never a generic share sheet.
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
     * Opens the default (or only) email app addressed to [EMAIL].
     * Returns false if no mail app can handle the request.
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
            openWithLogAttachment(context, subject, body)
        } else {
            openMailto(context, subject, body)
        }
    }

    private fun openMailto(context: Context, subject: String, body: String): Boolean {
        // Gmail / Samsung Email / Outlook ignore EXTRA_TEXT on ACTION_SENDTO mailto. Suggestions
        // default to attachLogs=false and hit this path — the compose window opened empty. Prefer
        // ACTION_SEND (message/rfc822) so EXTRA_TEXT actually fills the draft; still restricted to
        // real mail packages via launchMailOnly.
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchMailOnly(context, sendIntent, attachmentUri = null)) return true

        // Fallback: body embedded in the mailto URI (capped — some stacks reject huge URIs).
        val uri = Uri.parse(
            "mailto:$EMAIL" +
                "?subject=${Uri.encode(subject)}" +
                "&body=${Uri.encode(body.take(3500))}",
        )
        val mailtoIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = uri
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchMailOnly(context, mailtoIntent, attachmentUri = null)
    }

    private fun openWithLogAttachment(
        context: Context,
        subject: String,
        body: String,
    ): Boolean {
        val attachment = runCatching {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            // All log types in one file: crash + app.log + system exits + playback RAM log.
            val fileBody = AppLogger.buildFullShareBundle(
                context = context,
                reason = "feedback",
                prepend = body,
            )
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
        // ACTION_SEND + package of a mail app: attachments work; generic chooser does NOT.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(attachment.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchMailOnly(context, intent, attachmentUri = uri)
    }

    /**
     * Prefer the default mailto handler. Never open WhatsApp / Drive / Files share targets.
     */
    private fun launchMailOnly(
        context: Context,
        intent: Intent,
        attachmentUri: Uri?,
    ): Boolean {
        val pm = context.packageManager
        val mailPackages = resolveMailPackages(pm)
        if (mailPackages.isEmpty()) return false

        val defaultPkg = resolveDefaultMailPackage(pm)
        val preferred = listOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.samsung.android.email.provider",
            "com.yahoo.mobile.client.android.mail",
        )
        val targetPkg = when {
            defaultPkg != null && defaultPkg in mailPackages -> defaultPkg
            mailPackages.size == 1 -> mailPackages.first()
            else -> preferred.firstOrNull { it in mailPackages }
        }

        if (targetPkg != null) {
            intent.setPackage(targetPkg)
            if (attachmentUri != null) {
                runCatching {
                    context.grantUriPermission(
                        targetPkg,
                        attachmentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            return startOrFalse(context, intent)
        }

        // Several mail apps, none preferred: show ONLY those packages (never WhatsApp/Drive/etc.).
        val targeted = mailPackages.map { pkg ->
            Intent(intent).apply {
                setPackage(pkg)
                if (attachmentUri != null) {
                    runCatching {
                        context.grantUriPermission(
                            pkg,
                            attachmentUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
        }.toTypedArray()
        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            // Empty SENDTO mailto as the "base" so the system does not expand to every ACTION_SEND target.
            putExtra(
                Intent.EXTRA_INTENT,
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
            )
            putExtra(Intent.EXTRA_INITIAL_INTENTS, targeted)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startOrFalse(context, chooser)
    }

    private fun resolveDefaultMailPackage(pm: PackageManager): String? {
        val mailto = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.resolveActivity(mailto, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(mailto, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName
    }

    private fun resolveMailPackages(pm: PackageManager): List<String> {
        val mailto = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(mailto, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mailto, 0)
        }
        return list.mapNotNull { it.activityInfo?.packageName }.distinct()
    }

    private fun startOrFalse(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
