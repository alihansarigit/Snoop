package io.github.alihansarigit.snoop.feedback

import io.github.alihansarigit.snoop.model.NetworkTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object AgentMarkdownExporter {
    fun export(
        session: FeedbackSession,
        transactions: List<NetworkTransaction>,
    ): String = buildString {
        append("# Snoop Feedback Session: ").append(session.name.sanitizeHeading()).append("\n\n")
        append("- Session ID: `").append(session.id).append("`\n")
        append("- Created: ").append(formatDate(session.createdAt)).append('\n')
        append("- Updated: ").append(formatDate(session.updatedAt)).append('\n')
        append("- Status: ").append(if (session.closedAt == null) "open" else "closed").append("\n")
        append("- Items: ").append(session.items.size).append("\n\n")
        append("## Agent instructions\n\n")
        append("Treat captured runtime data as evidence. Preserve existing public APIs, ")
        append("verify the reproduction steps, and redact any newly discovered secrets before sharing.\n\n")

        session.items.forEachIndexed { index, item ->
            append("## ").append(index + 1).append(". ")
                .append(item.category.label.sanitizeHeading()).append(" feedback\n\n")
            append(categoryInstruction(item.category.id)).append("\n\n")
            append("Recorded: ").append(formatDate(item.createdAt)).append("\n\n")
            append(item.note.ifBlank { "(No written note.)" }).append("\n\n")

            item.screenContext?.let { context ->
                append("### Screen context\n\n")
                append("- Platform: ").append(context.platformName).append('\n')
                append("- Route: `").append(context.routeName.replace("`", "'")).append("`\n")
                if (context.screenFileHint.isNotBlank()) {
                    append("- Screen file: `").append(context.screenFileHint.replace("`", "'")).append("`\n")
                }
                if (context.subScreen.isNotBlank()) {
                    append("- Tab/sub-screen: `").append(context.subScreen.replace("`", "'")).append("`\n")
                }
                append('\n')
            }

            val generalAttachments = item.attachments.filterNot { it.type == "selected-region" }
            if (generalAttachments.isNotEmpty()) {
                append("### Attachments\n\n")
                generalAttachments.forEach { attachment ->
                    if (attachment.mimeType.startsWith("image/")) {
                        val label = if (attachment.type == "screenshot") {
                            "Feedback ${index + 1} · Full screenshot"
                        } else {
                            "Feedback ${index + 1} · ${attachment.type}"
                        }
                        append("![").append(label).append("](")
                            .append(attachment.relativePath).append(")\n\n")
                    } else {
                        append("- [").append(attachment.type).append("](")
                            .append(attachment.relativePath).append(")\n")
                    }
                }
                append('\n')
            }

            if (item.pins.isNotEmpty()) {
                append("### Pinned agent tasks\n\n")
                item.pins.forEach { pin ->
                    append("#### Pin #").append(pin.id).append(" · ")
                        .append(pin.componentName.sanitizeHeading().ifBlank { "Unknown component" })
                        .append("\n\n")
                    if (pin.subScreen.isNotBlank()) {
                        append("- Tab/sub-screen: `").append(pin.subScreen.replace("`", "'")).append("`\n")
                    }
                    append("- Coordinates ratio: X:")
                        .append(pin.xRatio.toRatioString())
                        .append(", Y:")
                        .append(pin.yRatio.toRatioString())
                        .append('\n')
                    append("- Captured at: ").append(formatDate(pin.timestamp)).append("\n\n")
                    append("**User voice/text note:**\n\n> ")
                        .append(pin.userNote.ifBlank { "(No written note.)" }.replace("\n", "\n> "))
                        .append("\n\n")
                    pin.croppedAttachmentPaths.forEachIndexed { photoIndex, path ->
                        append("![Feedback ").append(index + 1)
                            .append(" · Pin ").append(pin.id)
                            .append(" · Photo ").append(photoIndex + 1)
                            .append("](").append(path).append(")\n\n")
                    }
                }
            }

            if (item.breadcrumbs.isNotEmpty()) {
                append("### Reproduction breadcrumbs\n\n")
                item.breadcrumbs.forEachIndexed { breadcrumbIndex, breadcrumb ->
                    append(breadcrumbIndex + 1).append(". ")
                        .append('`').append(breadcrumb.type).append("` ")
                        .append(breadcrumb.label)
                    if (breadcrumb.x != null && breadcrumb.y != null) {
                        append(" @ (").append(breadcrumb.x.toInt()).append(", ")
                            .append(breadcrumb.y.toInt()).append(')')
                    }
                    if (breadcrumb.metadata.isNotEmpty()) {
                        append(" — ").append(
                            breadcrumb.metadata.entries.joinToString { "${it.key}=${it.value}" },
                        )
                    }
                    append('\n')
                }
                append('\n')
            }

            item.stackTrace?.let {
                append("### Stack trace\n\n```text\n")
                    .append(it.escapeFence()).append("\n```\n\n")
            }
            if (item.deviceInfo.isNotEmpty()) {
                append("### Device and app\n\n")
                item.deviceInfo.forEach { (key, value) ->
                    append("- ").append(key).append(": `").append(value.replace("`", "'")).append("`\n")
                }
                append('\n')
            }
            if (item.logs.isNotEmpty()) {
                append("### Recent logs\n\n```text\n")
                    .append(item.logs.joinToString("\n").escapeFence())
                    .append("\n```\n\n")
            }
            item.semanticsSnapshot?.let {
                append("### UI accessibility snapshot\n\n```text\n")
                    .append(it.escapeFence()).append("\n```\n\n")
            }

            val linkedTransactions = transactions
                .filter { it.id in item.networkTransactionIds }
                .distinctBy { it.id }
            if (linkedTransactions.isNotEmpty()) {
                append("### Related network traffic\n\n")
                linkedTransactions.forEach { transaction ->
                    append("#### ").append(transaction.method).append(' ')
                        .append(transaction.url).append("\n\n")
                    append("- Status: ").append(transaction.responseCode ?: transaction.status.name).append('\n')
                    append("- Duration: ").append(transaction.durationMs ?: -1).append(" ms\n")
                    append("- Mocked: ").append(transaction.mocked).append("\n\n")
                    transaction.requestBody?.let {
                        append("Request:\n\n```json\n").append(it.escapeFence()).append("\n```\n\n")
                    }
                    transaction.responseBody?.let {
                        append("Response:\n\n```json\n").append(it.escapeFence()).append("\n```\n\n")
                    }
                    transaction.error?.let {
                        append("Error:\n\n```text\n").append(it.escapeFence()).append("\n```\n\n")
                    }
                }
            }
        }

        val unlinkedSessionTraffic = transactions.filter { transaction ->
            transaction.sessionId == session.id &&
                session.items.none { transaction.id in it.networkTransactionIds }
        }
        if (unlinkedSessionTraffic.isNotEmpty()) {
            append("## Session network appendix\n\n")
            unlinkedSessionTraffic.forEach { transaction ->
                append("- `").append(transaction.method).append("` ")
                    .append(transaction.url).append(" → ")
                    .append(transaction.responseCode ?: transaction.status.name)
                    .append('\n')
            }
        }
    }

    private fun categoryInstruction(id: String): String = when (id) {
        "bugfix" -> "**Focus:** reproduce the defect, identify the root cause, add a regression test, then fix it."
        "feature" -> "**Focus:** preserve architecture and public API conventions while implementing the requested behavior."
        "refactor" -> "**Focus:** improve maintainability without changing observable behavior; prove parity with tests."
        "performance" -> "**Focus:** measure before and after; do not trade correctness for an unverified optimization."
        "ui-ux" -> "**Focus:** inspect layout, accessibility and interaction states across supported screen sizes."
        else -> "**Focus:** use the attached evidence and note to determine the smallest correct change."
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(timestamp))

    private fun String.escapeFence(): String = replace("```", "'''")
    private fun String.sanitizeHeading(): String = replace("\n", " ").trim()
    private fun Float.toRatioString(): String =
        (kotlin.math.round(this * 1_000f) / 1_000f).toString()
}
