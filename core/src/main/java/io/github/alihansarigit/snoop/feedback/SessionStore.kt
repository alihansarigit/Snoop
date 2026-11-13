package io.github.alihansarigit.snoop.feedback

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Crash-tolerant file store. Each session owns a small JSON manifest and an attachments
 * directory; manifests are replaced atomically so an interrupted write keeps the previous copy.
 */
internal class SessionStore(private val root: File) {
    private val lock = Any()
    private val sessionsRoot = File(root, "sessions")
    private val activeIdFile = File(root, "active-session")
    private val pendingCrashFile = File(root, "pending-crash.json")
    private val categoriesFile = File(root, "custom-categories.json")

    private val _sessions = MutableStateFlow<List<FeedbackSession>>(emptyList())
    val sessions: StateFlow<List<FeedbackSession>> = _sessions.asStateFlow()

    private val _active = MutableStateFlow<FeedbackSession?>(null)
    val active: StateFlow<FeedbackSession?> = _active.asStateFlow()

    init {
        load()
    }

    fun start(name: String): FeedbackSession = synchronized(lock) {
        val now = System.currentTimeMillis()
        val session = FeedbackSession(
            id = "${timestampId(now)}-${UUID.randomUUID().toString().take(8)}",
            name = name.trim().ifEmpty { "Feedback ${displayTimestamp(now)}" },
            createdAt = now,
            updatedAt = now,
        )
        persist(session)
        setActive(session)
        replaceInList(session)
        session
    }

    fun resume(id: String): FeedbackSession? = synchronized(lock) {
        val session = _sessions.value.firstOrNull { it.id == id && it.closedAt == null }
            ?: return@synchronized null
        setActive(session)
        session
    }

    fun closeActive(): FeedbackSession? = synchronized(lock) {
        val current = _active.value ?: return@synchronized null
        val now = System.currentTimeMillis()
        val closed = current.copy(updatedAt = now, closedAt = now)
        persist(closed)
        replaceInList(closed)
        _active.value = null
        activeIdFile.delete()
        closed
    }

    fun ensureActive(): FeedbackSession = synchronized(lock) {
        _active.value ?: start("Quick Feedback ${displayTimestamp(System.currentTimeMillis())}")
    }

    fun addFeedback(
        note: String,
        category: FeedbackCategory,
        screenshotPng: ByteArray?,
        semanticsSnapshot: String?,
        networkTransactionIds: List<Long>,
        stackTrace: String? = null,
        deviceInfo: Map<String, String> = emptyMap(),
        logs: List<String> = emptyList(),
        breadcrumbs: List<Breadcrumb> = BreadcrumbRecorder.snapshot(),
        screenContext: FeedbackScreenContext? = null,
        capturedPins: List<CapturedFeedbackPin> = emptyList(),
    ): FeedbackItem = synchronized(lock) {
        val session = ensureActive()
        addFeedbackToSessionLocked(
            session = session,
            note = note,
            category = category,
            screenshotPng = screenshotPng,
            semanticsSnapshot = semanticsSnapshot,
            networkTransactionIds = networkTransactionIds,
            stackTrace = stackTrace,
            deviceInfo = deviceInfo,
            logs = logs,
            breadcrumbs = breadcrumbs,
            screenContext = screenContext,
            capturedPins = capturedPins,
        )
    }

    fun addFeedbackToSession(
        sessionId: String,
        note: String,
        category: FeedbackCategory,
        screenshotPng: ByteArray?,
        semanticsSnapshot: String?,
        networkTransactionIds: List<Long>,
        stackTrace: String? = null,
        deviceInfo: Map<String, String> = emptyMap(),
        logs: List<String> = emptyList(),
        breadcrumbs: List<Breadcrumb> = BreadcrumbRecorder.snapshot(),
        screenContext: FeedbackScreenContext? = null,
        capturedPins: List<CapturedFeedbackPin> = emptyList(),
    ): FeedbackItem? = synchronized(lock) {
        val session = _sessions.value.firstOrNull {
            it.id == sessionId && it.closedAt == null
        } ?: return@synchronized null
        addFeedbackToSessionLocked(
            session = session,
            note = note,
            category = category,
            screenshotPng = screenshotPng,
            semanticsSnapshot = semanticsSnapshot,
            networkTransactionIds = networkTransactionIds,
            stackTrace = stackTrace,
            deviceInfo = deviceInfo,
            logs = logs,
            breadcrumbs = breadcrumbs,
            screenContext = screenContext,
            capturedPins = capturedPins,
        )
    }

    private fun addFeedbackToSessionLocked(
        session: FeedbackSession,
        note: String,
        category: FeedbackCategory,
        screenshotPng: ByteArray?,
        semanticsSnapshot: String?,
        networkTransactionIds: List<Long>,
        stackTrace: String?,
        deviceInfo: Map<String, String>,
        logs: List<String>,
        breadcrumbs: List<Breadcrumb>,
        screenContext: FeedbackScreenContext?,
        capturedPins: List<CapturedFeedbackPin>,
    ): FeedbackItem {
        val now = System.currentTimeMillis()
        val itemId = UUID.randomUUID().toString()
        val attachments = mutableListOf<FeedbackAttachment>()
        if (screenshotPng != null) {
            val relative = "attachments/$itemId-screenshot.png"
            val destination = File(sessionDirectory(session.id), relative)
            writeAtomically(destination, screenshotPng)
            attachments += FeedbackAttachment("screenshot", relative, "image/png", now)
        }
        val pins = capturedPins.map { pin ->
            val paths = pin.croppedRegionPng.mapIndexed { index, png ->
                val relative = "attachments/$itemId-pin${pin.id}-${index + 1}.png"
                val destination = File(sessionDirectory(session.id), relative)
                writeAtomically(destination, png)
                attachments += FeedbackAttachment("selected-region", relative, "image/png", now)
                relative
            }
            FeedbackPin(
                id = pin.id,
                xRatio = pin.xRatio,
                yRatio = pin.yRatio,
                componentName = pin.componentName,
                userNote = pin.userNote,
                timestamp = pin.timestamp,
                subScreen = pin.subScreen,
                croppedAttachmentPaths = paths,
            )
        }
        val item = FeedbackItem(
            id = itemId,
            createdAt = now,
            category = category,
            note = note.trim(),
            attachments = attachments,
            breadcrumbs = breadcrumbs,
            semanticsSnapshot = semanticsSnapshot,
            networkTransactionIds = networkTransactionIds.distinct(),
            stackTrace = stackTrace,
            deviceInfo = deviceInfo,
            logs = logs,
            screenContext = screenContext,
            pins = pins,
        )
        val updated = session.copy(updatedAt = now, items = session.items + item)
        persist(updated)
        replaceInList(updated)
        if (_active.value?.id == session.id) setActive(updated)
        return item
    }

    fun customCategories(): List<FeedbackCategory> = synchronized(lock) {
        runCatching {
            if (!categoriesFile.exists()) return@synchronized emptyList()
            val array = org.json.JSONArray(categoriesFile.readText())
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let {
                    runCatching {
                        FeedbackCategory(
                            id = it.getString("id"),
                            label = it.getString("label"),
                            colorArgb = it.getInt("colorArgb"),
                        )
                    }.getOrNull()
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addCustomCategory(label: String, colorArgb: Int): FeedbackCategory = synchronized(lock) {
        val normalized = label.trim().ifEmpty { "Custom" }
        val category = FeedbackCategory(
            id = "custom-${UUID.randomUUID().toString().take(8)}",
            label = normalized.take(40),
            colorArgb = colorArgb,
        )
        val categories = customCategories() + category
        val array = org.json.JSONArray()
        categories.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("label", it.label)
                    .put("colorArgb", it.colorArgb),
            )
        }
        writeAtomically(categoriesFile, array.toString(2).toByteArray())
        category
    }

    fun sessionDirectory(sessionId: String): File = File(sessionsRoot, sessionId)

    fun find(sessionId: String): FeedbackSession? =
        _sessions.value.firstOrNull { it.id == sessionId }

    fun readAttachment(sessionId: String, relativePath: String): ByteArray? = synchronized(lock) {
        val sessionRoot = sessionDirectory(sessionId).canonicalFile
        val attachment = File(sessionRoot, relativePath).canonicalFile
        if (!attachment.path.startsWith("${sessionRoot.path}${File.separator}")) {
            return@synchronized null
        }
        attachment.takeIf(File::isFile)?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    fun delete(sessionId: String): Boolean = synchronized(lock) {
        val session = _sessions.value.firstOrNull { it.id == sessionId }
            ?: return@synchronized false
        val directory = sessionDirectory(session.id)
        if (directory.exists() && !directory.deleteRecursively()) {
            return@synchronized false
        }
        _sessions.value = _sessions.value.filterNot { it.id == sessionId }
        if (_active.value?.id == sessionId) {
            _active.value = null
            activeIdFile.delete()
        }
        true
    }

    fun writeExport(sessionId: String, markdown: String): File {
        val destination = File(sessionDirectory(sessionId), "feedback.md")
        synchronized(lock) {
            writeAtomically(destination, markdown.toByteArray())
        }
        return destination
    }

    /**
     * @param bundleName file name inside the session directory. A partial export must
     *   not overwrite the full-session bundle, so it passes its own name.
     * @param manifestJson replaces the on-disk session manifest. A partial export passes
     *   a filtered manifest; copying the full one would leak the unselected pins.
     */
    fun writeAgentBundle(
        sessionId: String,
        markdown: String,
        attachmentPaths: List<String>,
        bundleName: String = "$sessionId-agent-feedback.zip",
        manifestJson: String? = null,
    ): File = synchronized(lock) {
        val sessionRoot = sessionDirectory(sessionId)
        val destination = File(sessionRoot, bundleName)
        val archive = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("feedback.md"))
                zip.write(markdown.toByteArray())
                zip.closeEntry()
                val manifestBytes = manifestJson?.toByteArray()
                    ?: File(sessionRoot, MANIFEST_FILE).takeIf(File::isFile)?.readBytes()
                manifestBytes?.let { manifest ->
                    zip.putNextEntry(ZipEntry(MANIFEST_FILE))
                    zip.write(manifest)
                    zip.closeEntry()
                }
                attachmentPaths.distinct().forEach { relativePath ->
                    val attachment = File(sessionRoot, relativePath).canonicalFile
                    if (
                        attachment.isFile &&
                        attachment.path.startsWith("${sessionRoot.canonicalPath}${File.separator}")
                    ) {
                        zip.putNextEntry(ZipEntry(relativePath))
                        zip.write(attachment.readBytes())
                        zip.closeEntry()
                    }
                }
            }
            bytes.toByteArray()
        }
        writeAtomically(destination, archive)
        destination
    }

    fun writePendingCrash(crash: PendingCrash, screenshotPng: ByteArray?) = synchronized(lock) {
        val enriched = if (screenshotPng == null) {
            crash
        } else {
            val relative = "pending-crash-screenshot.png"
            writeAtomically(File(root, relative), screenshotPng)
            crash.copy(screenshotRelativePath = relative)
        }
        writeAtomically(pendingCrashFile, enriched.toJson().toString().toByteArray())
    }

    fun readPendingCrash(): PendingCrash? = synchronized(lock) {
        runCatching {
            if (!pendingCrashFile.exists()) null
            else JSONObject(pendingCrashFile.readText()).toPendingCrash()
        }.getOrNull()
    }

    fun readPendingCrashScreenshot(crash: PendingCrash): ByteArray? = synchronized(lock) {
        crash.screenshotRelativePath
            ?.let { File(root, it) }
            ?.takeIf(File::exists)
            ?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    fun attachPendingCrash(): FeedbackItem? = synchronized(lock) {
        val crash = readPendingCrash() ?: return@synchronized null
        val screenshot = crash.screenshotRelativePath
            ?.let { File(root, it) }
            ?.takeIf(File::exists)
            ?.readBytes()
        val item = addFeedback(
            note = "Unexpected crash on thread ${crash.threadName}",
            category = BuiltInCategories.Bugfix,
            screenshotPng = screenshot,
            semanticsSnapshot = null,
            networkTransactionIds = emptyList(),
            stackTrace = crash.stackTrace,
            deviceInfo = crash.deviceInfo,
            logs = crash.logs,
            breadcrumbs = crash.breadcrumbs,
        )
        clearPendingCrash()
        item
    }

    fun clearPendingCrash() = synchronized(lock) {
        readPendingCrash()?.screenshotRelativePath?.let { File(root, it).delete() }
        pendingCrashFile.delete()
    }

    private fun load() = synchronized(lock) {
        val loaded = sessionsRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .map { File(it, MANIFEST_FILE) }
            .filter(File::exists)
            .mapNotNull { file ->
                runCatching { JSONObject(file.readText()).toFeedbackSession() }.getOrNull()
            }
            .sortedByDescending { it.updatedAt }
            .toList()
        _sessions.value = loaded
        val activeId = runCatching { activeIdFile.readText().trim() }.getOrNull()
        _active.value = loaded.firstOrNull { it.id == activeId && it.closedAt == null }
    }

    private fun setActive(session: FeedbackSession) {
        _active.value = session
        writeAtomically(activeIdFile, session.id.toByteArray())
    }

    private fun replaceInList(session: FeedbackSession) {
        _sessions.value = (_sessions.value.filterNot { it.id == session.id } + session)
            .sortedByDescending { it.updatedAt }
    }

    private fun persist(session: FeedbackSession) {
        val file = File(sessionDirectory(session.id), MANIFEST_FILE)
        writeAtomically(file, session.toJson().toString(2).toByteArray())
    }

    private fun writeAtomically(destination: File, bytes: ByteArray) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.writeBytes(bytes)
        val replaced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            moveAtomically(temporary, destination)
        } else {
            temporary.renameTo(destination)
        }
        if (!replaced) {
            destination.writeBytes(bytes)
            temporary.delete()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun moveAtomically(source: File, destination: File): Boolean = runCatching {
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        true
    }.getOrDefault(false)

    private fun timestampId(timestamp: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestamp))

    private fun displayTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
    }
}
