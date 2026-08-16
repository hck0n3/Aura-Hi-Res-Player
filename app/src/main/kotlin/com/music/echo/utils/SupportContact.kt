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
        screenshotUris: List<Uri> = emptyList(),
    ): Boolean {
        val header = DiagnosticHeader.build(context, "feedback")
        val body = buildString {
            appendLine(userMessage.trim().ifBlank { "(no message)" })
            appendLine()
            append(header)
        }
        val subject = subject(kind)
        val shots = screenshotUris.filter { it != Uri.EMPTY }
        return if (attachLogs || shots.isNotEmpty()) {
            openWithAttachments(context, subject, body, attachLogs, shots)
        } else {
            openMailto(context, subject, body)
        }
    }

    /**
     * Opens the email client pre-filling the crash report to [EMAIL].
     */
    fun openCrashReport(context: Context, crashLog: String): Boolean {
        return openWithAttachments(
            context = context,
            subject = "[Aura Crash] Report ${iad1tya.echo.music.BuildConfig.VERSION_NAME} (${iad1tya.echo.music.BuildConfig.VERSION_CODE})",
            body = crashLog,
            attachLogs = true,
            screenshotUris = emptyList(),
        )
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
        if (launchMailOnly(context, sendIntent, attachmentUris = emptyList())) return true

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
        return launchMailOnly(context, mailtoIntent, attachmentUris = emptyList())
    }

    private fun openWithAttachments(
        context: Context,
        subject: String,
        body: String,
        attachLogs: Boolean,
        screenshotUris: List<Uri>,
    ): Boolean {
        val uris = ArrayList<Uri>()
        if (attachLogs) {
            val attachment = runCatching {
                val dir = File(context.filesDir, "logs").apply { mkdirs() }
                val fileBody = AppLogger.buildFullShareBundle(
                    context = context,
                    reason = "feedback",
                    prepend = body,
                )
                File(dir, "aura_feedback.txt").also { it.writeText(fileBody) }
            }.getOrNull()
            if (attachment != null && attachment.exists()) {
                uris.add(
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.FileProvider",
                        attachment,
                    ),
                )
            }
        }
        uris.addAll(screenshotUris)
        if (uris.isEmpty()) {
            return openMailto(context, subject, body)
        }

        val multiple = uris.size > 1
        val intent = Intent(
            if (multiple) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND,
        ).apply {
            type = if (screenshotUris.isNotEmpty()) "*/*" else "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (multiple) {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            } else {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
            clipData = android.content.ClipData.newRawUri("attachment", uris.first()).also { clip ->
                uris.drop(1).forEach { uri -> clip.addItem(android.content.ClipData.Item(uri)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchMailOnly(context, intent, attachmentUris = uris)
    }

    /**
     * Prefer the default mailto handler. Never open WhatsApp / Drive / Files share targets.
     */
    private fun launchMailOnly(
        context: Context,
        intent: Intent,
        attachmentUris: List<Uri>,
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
            attachmentUris.forEach { uri ->
                runCatching {
                    context.grantUriPermission(
                        targetPkg,
                        uri,
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
                attachmentUris.forEach { uri ->
                    runCatching {
                        context.grantUriPermission(
                            pkg,
                            uri,
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
