package io.github.alihansarigit.snoop.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Writes an agent bundle into device storage so it can be collected with
 * `adb pull` or the system Files app. This is the fallback for the two cases the
 * share sheet and the desktop bridge do not cover: a bare emulator with no share
 * targets, and a machine where the Snoop daemon is not running.
 */
internal object FeedbackDownload {

    /** Returns the on-device location the bundle was written to. */
    fun saveAgentBundle(context: Context, fileName: String, bytes: ByteArray): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveInSharedDownloads(context, fileName, bytes)
        } else {
            saveInAppExternalFiles(context, fileName, bytes)
        }

    /**
     * API 29+ writes into the shared Downloads collection, which needs no
     * permission and stays readable after the app is uninstalled.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveInSharedDownloads(
        context: Context,
        fileName: String,
        bytes: ByteArray,
    ): String {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, ZIP_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_DIRECTORY)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: error("Downloads folder is not writable")
        runCatching {
            resolver.openOutputStream(uri)?.use { output -> output.write(bytes) }
                ?: error("Downloads folder is not writable")
        }.onFailure { failure ->
            runCatching { resolver.delete(uri, null, null) }
            throw failure
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        // MediaStore renames collisions to "name (1).zip", so report what it stored.
        val storedName = resolver.query(
            uri,
            arrayOf(MediaStore.Downloads.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: fileName
        return "$PUBLIC_STORAGE_ROOT/$RELATIVE_DIRECTORY$storedName"
    }

    /**
     * Before API 29 the shared collection would need `WRITE_EXTERNAL_STORAGE`.
     * Snoop stays permission-free and uses its own external directory instead;
     * `adb pull` reaches it, the Files app does not.
     */
    private fun saveInAppExternalFiles(
        context: Context,
        fileName: String,
        bytes: ByteArray,
    ): String {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("External storage is unavailable")
        require(directory.isDirectory || directory.mkdirs()) {
            "Download directory cannot be created"
        }
        val destination = File(directory, fileName)
        destination.writeBytes(bytes)
        return destination.absolutePath
    }

    private const val ZIP_MIME_TYPE = "application/zip"
    private val RELATIVE_DIRECTORY = "${Environment.DIRECTORY_DOWNLOADS}/Snoop/"
    private const val PUBLIC_STORAGE_ROOT = "/sdcard"
}
