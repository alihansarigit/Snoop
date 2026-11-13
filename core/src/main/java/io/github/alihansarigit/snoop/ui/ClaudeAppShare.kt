package io.github.alihansarigit.snoop.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.alihansarigit.snoop.feedback.FeedbackSharePayload
import io.github.alihansarigit.snoop.feedback.SnoopAttachmentProvider

/**
 * Hands selected feedback pins straight to the Claude Android app instead of the
 * system chooser. The package is declared in `<queries>` so it stays visible under
 * Android 11+ package filtering.
 */
internal object ClaudeAppShare {
    const val PACKAGE_NAME = "com.anthropic.claude"

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME) != null
        }.getOrDefault(false)

    /**
     * Sends [payload] to Claude. Returns `false` when Claude is not installed or
     * cannot accept the intent, so the caller can fall back to the system chooser.
     */
    fun sendSelectedPins(
        context: Context,
        sessionId: String,
        sessionName: String,
        payload: FeedbackSharePayload,
    ): Boolean {
        val imageUris = payload.attachments
            .filter { it.mimeType.startsWith("image/") }
            .map { SnoopAttachmentProvider.uri(context, sessionId, it.relativePath) }
        val intent = buildIntent(sessionName, payload.markdown, imageUris).apply {
            setPackage(PACKAGE_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            return false
        }
        // Explicit grants: Claude resolves the send target in its own process, and
        // ACTION_SEND_MULTIPLE only propagates the flag to URIs it can see up front.
        imageUris.forEach { uri ->
            context.grantUriPermission(
                PACKAGE_NAME,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    internal fun buildIntent(
        sessionName: String,
        markdown: String,
        imageUris: List<Uri>,
    ): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (imageUris.isEmpty()) "text/plain" else "image/*"
            putExtra(Intent.EXTRA_TEXT, markdown)
        }
        if (imageUris.isNotEmpty()) {
            val firstImage = imageUris.first()
            intent.clipData = ClipData(
                "Snoop feedback",
                arrayOf("image/*"),
                ClipData.Item(markdown, null, null, firstImage),
            ).apply {
                imageUris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            if (imageUris.size == 1) {
                intent.putExtra(Intent.EXTRA_STREAM, firstImage)
            } else {
                intent.action = Intent.ACTION_SEND_MULTIPLE
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
            }
        }
        return intent.putExtra(Intent.EXTRA_SUBJECT, "Snoop feedback · $sessionName")
    }
}
