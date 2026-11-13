package io.github.alihansarigit.snoop.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import io.github.alihansarigit.snoop.feedback.FeedbackAttachment
import io.github.alihansarigit.snoop.feedback.FeedbackItem
import io.github.alihansarigit.snoop.feedback.FeedbackSession
import io.github.alihansarigit.snoop.feedback.SnoopAttachmentProvider

internal object FeedbackClipboard {
    fun copySession(
        context: Context,
        session: FeedbackSession,
        markdown: String,
    ): Int = copyTextAndAttachments(
        context = context,
        label = "Snoop session · ${session.name}",
        text = markdown,
        sessionId = session.id,
        attachments = session.items.flatMap(FeedbackItem::attachments),
    )

    fun copyItem(
        context: Context,
        sessionId: String,
        item: FeedbackItem,
        markdown: String,
    ): Int = copyTextAndAttachments(
        context = context,
        label = "Snoop feedback · ${item.category.label}",
        text = markdown,
        sessionId = sessionId,
        attachments = item.attachments,
    )

    fun copyAttachment(
        context: Context,
        sessionId: String,
        attachment: FeedbackAttachment,
        label: String,
    ) {
        val uri = SnoopAttachmentProvider.uri(context, sessionId, attachment.relativePath)
        val clip = ClipData(
            label,
            arrayOf(attachment.mimeType),
            ClipData.Item(uri),
        )
        clipboard(context).setPrimaryClip(clip)
    }

    private fun copyTextAndAttachments(
        context: Context,
        label: String,
        text: String,
        sessionId: String,
        attachments: List<FeedbackAttachment>,
    ): Int {
        val uniqueAttachments = attachments.distinctBy(FeedbackAttachment::relativePath)
        val mimeTypes = buildList {
            add(ClipDescription.MIMETYPE_TEXT_PLAIN)
            uniqueAttachments.mapTo(this, FeedbackAttachment::mimeType)
        }.distinct().toTypedArray()
        val clip = ClipData(label, mimeTypes, ClipData.Item(text))
        uniqueAttachments.forEach { attachment ->
            clip.addItem(
                ClipData.Item(
                    SnoopAttachmentProvider.uri(context, sessionId, attachment.relativePath),
                ),
            )
        }
        clipboard(context).setPrimaryClip(clip)
        return uniqueAttachments.size
    }

    private fun clipboard(context: Context): ClipboardManager =
        requireNotNull(context.getSystemService(ClipboardManager::class.java))
}
