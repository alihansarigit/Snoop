package io.github.alihansarigit.snoop.daemon

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * Loopback-only endpoint used by Android debug builds through `adb reverse`.
 *
 * The device never chooses a desktop path. Every export is constrained to the
 * project directory selected when this daemon process starts.
 */
internal class DesktopBridgeServer(
    projectDirectory: File,
    private val expectedPackage: String?,
    port: Int,
) : Closeable {
    private val projectRoot = projectDirectory.canonicalFile
    private val running = AtomicBoolean(true)
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
    }

    val boundPort: Int
        get() = server.localPort

    /**
     * Wall-clock of the last handled request, used by the idle watchdog so an
     * auto-started bridge does not outlive the working day.
     */
    @Volatile
    var lastActivityAt: Long = System.currentTimeMillis()
        private set

    init {
        require(projectRoot.isDirectory || projectRoot.mkdirs()) {
            "Project directory cannot be created: ${projectRoot.absolutePath}"
        }
    }

    fun serve() {
        while (running.get()) {
            val socket = runCatching { server.accept() }.getOrElse {
                if (running.get()) throw it
                return
            }
            socket.use(::handle)
        }
    }

    override fun close() {
        running.set(false)
        server.close()
    }

    private fun handle(socket: Socket) {
        lastActivityAt = System.currentTimeMillis()
        socket.soTimeout = CLIENT_TIMEOUT_MILLIS
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val response = runCatching {
            val request = JSONObject(readLine(input, MAX_HEADER_BYTES))
            validatePackage(request.getString("packageName"))
            when (request.getString("operation")) {
                "status" -> statusResponse()
                "export" -> export(request, input)
                else -> error("Unsupported operation")
            }
        }.getOrElse { error(it.message ?: "Bridge request failed") }
        output.write(response.toString().toByteArray(Charsets.UTF_8))
        output.write('\n'.code)
        output.flush()
    }

    private fun statusResponse(): JSONObject = JSONObject()
        .put("ok", true)
        .put("protocolVersion", PROTOCOL_VERSION)
        .put("projectName", projectRoot.name)
        .put("projectPath", projectRoot.absolutePath)

    private fun export(request: JSONObject, input: BufferedInputStream): JSONObject {
        val packageName = request.getString("packageName")
        val sessionId = request.getString("sessionId").also(::validateSafeName)
        val fileName = request.getString("fileName").also(::validateZipName)
        // Absent means the whole session. A partial export names the pins it carries.
        val selection = request.optString("selection").ifBlank { FULL_EXPORT_SEGMENT }
            .also(::validateSafeName)
        val contentLength = request.getLong("contentLength")
        require(contentLength in 1..MAX_ARCHIVE_BYTES) {
            "Archive size must be between 1 byte and $MAX_ARCHIVE_BYTES bytes"
        }

        val exportRoot = File(projectRoot, "agent-feedback").canonicalFile
        // Grouped by the day the transfer happened, then the app, then the session. Each
        // segment is validated above, so none of them can escape the export root.
        val destinationDirectory = exportRoot
            .resolve(exportDay())
            .resolve(packageName)
            .resolve(shortSessionId(sessionId))
            .resolve(selection)
            .canonicalFile
        require(destinationDirectory.path.startsWith("${exportRoot.path}${File.separator}")) {
            "Invalid export directory"
        }
        // Re-sending a session replaces it. Without this, attachments deleted in the
        // app since the previous transfer would linger and be read as current.
        if (destinationDirectory.exists()) {
            require(destinationDirectory.deleteRecursively()) {
                "Previous export cannot be replaced"
            }
        }
        require(destinationDirectory.mkdirs()) { "Export directory cannot be created" }

        val temporary = File(destinationDirectory, "$fileName.part")
        temporary.outputStream().buffered().use { destination ->
            copyExactly(input, destination, contentLength)
        }
        val archive = File(destinationDirectory, fileName)
        moveReplacing(temporary, archive)
        extractArchive(archive, destinationDirectory)

        return JSONObject()
            .put("ok", true)
            .put("projectName", projectRoot.name)
            .put("projectPath", projectRoot.absolutePath)
            .put("exportPath", destinationDirectory.absolutePath)
    }

    private fun extractArchive(archive: File, destinationDirectory: File) {
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val destination = File(destinationDirectory, entry.name).canonicalFile
                require(
                    destination.path.startsWith(
                        "${destinationDirectory.canonicalPath}${File.separator}",
                    ),
                ) {
                    "Unsafe ZIP entry"
                }
                if (entry.isDirectory) {
                    require(destination.isDirectory || destination.mkdirs()) {
                        "Cannot create ZIP directory"
                    }
                } else {
                    require(destination.parentFile?.isDirectory == true || destination.parentFile.mkdirs()) {
                        "Cannot create ZIP parent directory"
                    }
                    destination.outputStream().buffered().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun validatePackage(packageName: String) {
        require(packageName.matches(SAFE_PACKAGE)) { "Invalid Android package name" }
        require(expectedPackage.isNullOrBlank() || expectedPackage == packageName) {
            "Connected app does not match SNOOP_PACKAGE"
        }
    }

    private fun validateSafeName(value: String) {
        require(value.matches(SAFE_NAME)) { "Invalid session id" }
    }

    /** Host-local day of the transfer, which is what groups exports on disk. */
    private fun exportDay(): String = LocalDate.now().format(DAY_FORMAT)

    /**
     * Session ids are `yyyyMMdd-HHmmss-xxxxxxxx`. The day already names the parent
     * directory, so only the random suffix is kept here — the full id stays in
     * `manifest.json`.
     */
    private fun shortSessionId(sessionId: String): String =
        sessionId.substringAfterLast('-').ifBlank { sessionId }

    private fun validateZipName(value: String) {
        require(value.matches(SAFE_ZIP_NAME)) { "Invalid archive name" }
    }

    private fun error(message: String): JSONObject =
        JSONObject().put("ok", false).put("message", message.take(300))

    private fun readLine(input: BufferedInputStream, limit: Int): String {
        val bytes = ArrayList<Byte>()
        while (bytes.size < limit) {
            val value = input.read()
            if (value == -1) throw EOFException("Bridge request ended before its header")
            if (value == '\n'.code) return bytes.toByteArray().toString(Charsets.UTF_8)
            bytes += value.toByte()
        }
        throw IllegalArgumentException("Bridge request header is too large")
    }

    private fun copyExactly(input: BufferedInputStream, output: java.io.OutputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) throw EOFException("Archive upload ended early")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun moveReplacing(source: File, destination: File) {
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private companion object {
        // v2 groups exports by day/package/session/selection instead of one directory
        // per session id. An older app would write the wrong layout, so the version
        // check has to fail rather than accept it.
        const val PROTOCOL_VERSION = 2
        const val CLIENT_TIMEOUT_MILLIS = 30_000
        const val MAX_HEADER_BYTES = 8 * 1024
        const val MAX_ARCHIVE_BYTES = 100L * 1024L * 1024L
        const val FULL_EXPORT_SEGMENT = "full"
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val SAFE_PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        val SAFE_NAME = Regex("[A-Za-z0-9._-]{1,120}")
        val SAFE_ZIP_NAME = Regex("[A-Za-z0-9._-]{1,160}\\.zip")
    }
}
