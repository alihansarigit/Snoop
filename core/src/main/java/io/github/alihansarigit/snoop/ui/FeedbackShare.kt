package io.github.alihansarigit.snoop.ui

import android.content.ClipData
import android.content.Intent
import android.content.Context
import io.github.alihansarigit.snoop.feedback.FeedbackSharePayload
import io.github.alihansarigit.snoop.feedback.SnoopAttachmentProvider

internal object FeedbackShare {
    fun exportAgentBundle(
        context: Context,
        sessionId: String,
        bundlePath: String,
        sessionName: String,
    ) {
        val uri = SnoopAttachmentProvider.uri(context, sessionId, bundlePath)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Snoop feedback · $sessionName")
            clipData = ClipData.newRawUri("Snoop agent bundle", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Export feedback for agent"))
    }

    fun shareSelectedPins(
        context: Context,
        sessionId: String,
        sessionName: String,
        payload: FeedbackSharePayload,
    ) {
        val imageUris = payload.attachments
            .filter { it.mimeType.startsWith("image/") }
            .map {
                SnoopAttachmentProvider.uri(context, sessionId, it.relativePath)
            }
        val share = if (imageUris.isEmpty()) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_TEXT, payload.markdown)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_TEXT, payload.markdown)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
                clipData = ClipData.newPlainText(
                    "Selected Snoop feedback",
                    payload.markdown,
                ).apply {
                    imageUris.forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        share.putExtra(Intent.EXTRA_SUBJECT, "Selected feedback · $sessionName")
        context.startActivity(Intent.createChooser(share, "Share selected pins"))
    }
}
