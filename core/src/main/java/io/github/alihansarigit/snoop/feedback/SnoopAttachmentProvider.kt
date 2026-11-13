package io.github.alihansarigit.snoop.feedback

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import io.github.alihansarigit.snoop.Snoop
import java.io.FileNotFoundException

/**
 * Read-only bridge that lets clipboard consumers open feedback attachments without exposing
 * Snoop's private session directory.
 */
class SnoopAttachmentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? =
        resolve(uri)?.let { (sessionId, relativePath) ->
            when {
                relativePath.endsWith(".zip", ignoreCase = true) -> "application/zip"
                relativePath.endsWith(".md", ignoreCase = true) -> "text/markdown"
                relativePath.endsWith(".json", ignoreCase = true) -> "application/json"
                else -> Snoop.feedbackSessionSnapshot()
                    .firstOrNull { it.id == sessionId }
                    ?.items
                    ?.asSequence()
                    ?.flatMap { it.attachments.asSequence() }
                    ?.firstOrNull { it.relativePath == relativePath }
                    ?.mimeType
            }
        }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val (sessionId, relativePath) = resolve(uri) ?: return null
        val bytes = Snoop.feedbackAttachment(sessionId, relativePath) ?: return null
        val requested = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(requested).apply {
            addRow(
                requested.map { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> relativePath.substringAfterLast('/')
                        OpenableColumns.SIZE -> bytes.size.toLong()
                        else -> null
                    }
                },
            )
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Feedback attachments are read-only")
        val (sessionId, relativePath) = resolve(uri)
            ?: throw FileNotFoundException("Invalid feedback attachment URI")
        val bytes = Snoop.feedbackAttachment(sessionId, relativePath)
            ?: throw FileNotFoundException("Feedback attachment is unavailable")
        val pipe = ParcelFileDescriptor.createPipe()
        Thread(
            {
                runCatching {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                        output.write(bytes)
                    }
                }.onFailure { error ->
                    runCatching { pipe[1].closeWithError(error.message ?: "Attachment read failed") }
                }
            },
            "snoop-clipboard-attachment",
        ).apply {
            isDaemon = true
            start()
        }
        return pipe[0]
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Feedback attachments are read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Feedback attachments are read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Feedback attachments are read-only")

    private fun resolve(uri: Uri): Pair<String, String>? {
        if (uri.authority != requireNotNull(context).attachmentAuthority()) return null
        val segments = uri.pathSegments
        if (segments.size != 2) return null
        return segments[0] to segments[1]
    }

    internal companion object {
        fun uri(
            context: Context,
            sessionId: String,
            relativePath: String,
        ): Uri = Uri.Builder()
            .scheme("content")
            .authority(context.attachmentAuthority())
            .appendPath(sessionId)
            .appendPath(relativePath)
            .build()

        private fun Context.attachmentAuthority(): String = "$packageName.snoop.attachments"
    }
}
