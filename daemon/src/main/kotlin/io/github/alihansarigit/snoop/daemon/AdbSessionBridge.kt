package io.github.alihansarigit.snoop.daemon

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class AdbSessionBridge(
    private val adb: String = System.getenv("ADB")?.takeIf(String::isNotBlank) ?: "adb",
) {
    fun connectedDevices(): List<String> =
        text("devices")
            .lineSequence()
            .drop(1)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { line ->
                val columns = line.split(Regex("\\s+"))
                columns.firstOrNull()?.takeIf { columns.getOrNull(1) == "device" }
            }
            .toList()

    fun connectReverse(port: Int, devices: List<String>) {
        require(port in 1..65535) { "Invalid bridge port" }
        devices.forEach { serial ->
            text("-s", serial, "reverse", "tcp:$port", "tcp:$port")
        }
    }

    fun listSessions(packageName: String): JSONArray {
        validatePackage(packageName)
        val listing = text(
            "shell",
            "run-as",
            packageName,
            "ls",
            SESSION_ROOT,
        )
        val result = JSONArray()
        listing.lineSequence()
            .map(String::trim)
            .filter { it.matches(SAFE_NAME) }
            .forEach { sessionId ->
                runCatching { readSessionManifest(packageName, sessionId) }
                    .getOrNull()
                    ?.let(result::put)
            }
        return result
    }

    fun readSession(packageName: String, sessionId: String): String {
        validatePackage(packageName)
        validateSessionId(sessionId)
        val markdownPath = "$SESSION_ROOT/$sessionId/feedback.md"
        return runCatching {
            text("exec-out", "run-as", packageName, "cat", markdownPath)
        }.getOrElse {
            text(
                "exec-out",
                "run-as",
                packageName,
                "cat",
                "$SESSION_ROOT/$sessionId/manifest.json",
            )
        }
    }

    fun pullSessionArchive(
        packageName: String,
        sessionId: String,
        outputDirectory: File,
    ): File {
        validatePackage(packageName)
        validateSessionId(sessionId)
        require(outputDirectory.isDirectory || outputDirectory.mkdirs()) {
            "Output directory cannot be created: ${outputDirectory.absolutePath}"
        }
        val destination = File(outputDirectory, "$sessionId.tar")
        val bytes = binary(
            "exec-out",
            "run-as",
            packageName,
            "tar",
            "-cf",
            "-",
            "-C",
            SESSION_ROOT,
            sessionId,
        )
        require(bytes.isNotEmpty()) { "ADB returned an empty archive" }
        // `adb` can exit 0 while `tar` writes its own diagnostic to stdout, which would
        // otherwise be saved as a .tar that no extractor can open.
        require(isTarArchive(bytes)) {
            "ADB did not return a tar archive: ${bytes.toString(Charsets.UTF_8).trim().take(200)}"
        }
        destination.writeBytes(bytes)
        return destination
    }

    /** POSIX tar keeps the `ustar` magic at offset 257 of the first 512-byte header. */
    private fun isTarArchive(bytes: ByteArray): Boolean {
        if (bytes.size < TAR_HEADER_BYTES) return false
        val magic = bytes.copyOfRange(TAR_MAGIC_OFFSET, TAR_MAGIC_OFFSET + TAR_MAGIC.length)
        return magic.toString(Charsets.US_ASCII) == TAR_MAGIC
    }

    private fun readSessionManifest(packageName: String, sessionId: String): JSONObject =
        JSONObject(
            text(
                "exec-out",
                "run-as",
                packageName,
                "cat",
                "$SESSION_ROOT/$sessionId/manifest.json",
            ),
        )

    private fun text(vararg arguments: String): String =
        binary(*arguments).toString(Charsets.UTF_8).trim()

    private fun binary(vararg arguments: String): ByteArray {
        val process = ProcessBuilder(listOf(adb) + arguments)
            .redirectErrorStream(true)
            .start()
        val outputFuture = CompletableFuture.supplyAsync { process.inputStream.use { it.readBytes() } }
        val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw IllegalStateException("ADB command timed out")
        }
        val output = outputFuture.get(2, TimeUnit.SECONDS)
        if (process.exitValue() != 0) {
            throw IllegalStateException(output.toString(Charsets.UTF_8).trim().ifEmpty { "ADB command failed" })
        }
        return output
    }

    private fun validatePackage(value: String) {
        require(value.matches(SAFE_PACKAGE)) { "Invalid Android package name" }
    }

    private fun validateSessionId(value: String) {
        require(value.matches(SAFE_NAME)) { "Invalid session id" }
    }

    private companion object {
        const val SESSION_ROOT = "files/snoop/feedback/sessions"
        const val COMMAND_TIMEOUT_SECONDS = 30L
        const val TAR_HEADER_BYTES = 512
        const val TAR_MAGIC_OFFSET = 257
        const val TAR_MAGIC = "ustar"
        val SAFE_PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        val SAFE_NAME = Regex("[A-Za-z0-9._-]{1,120}")
    }
}
