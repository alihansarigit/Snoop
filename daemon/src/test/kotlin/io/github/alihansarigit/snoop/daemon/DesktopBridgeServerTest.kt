package io.github.alihansarigit.snoop.daemon

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopBridgeServerTest {
    @Test
    fun reportsConfiguredProjectAndExtractsAgentBundle() {
        val project = Files.createTempDirectory("snoop-bridge-project").toFile()
        try {
            withServer(project, "com.example.debug") { port ->
                val status = request(
                    port,
                    JSONObject()
                        .put("operation", "status")
                        .put("packageName", "com.example.debug"),
                )
                assertTrue(status.getBoolean("ok"))
                assertEquals(project.canonicalPath, status.getString("projectPath"))

                val archive = zip(
                    "feedback.md" to "# Feedback".toByteArray(),
                    "attachments/screen.png" to byteArrayOf(1, 2, 3),
                )
                val exported = request(
                    port,
                    JSONObject()
                        .put("operation", "export")
                        .put("packageName", "com.example.debug")
                        .put("sessionId", "session-123")
                        .put("fileName", "session-123-agent-feedback.zip")
                        .put("contentLength", archive.size),
                    archive,
                )

                assertTrue(exported.getBoolean("ok"))
                // day / package / short session id / selection
                val destination = exportDirectory(project, "com.example.debug", "123", "full")
                assertEquals(destination.canonicalPath, exported.getString("exportPath"))
                assertEquals("# Feedback", File(destination, "feedback.md").readText())
                assertTrue(
                    File(destination, "attachments/screen.png")
                        .readBytes()
                        .contentEquals(byteArrayOf(1, 2, 3)),
                )
                assertTrue(File(destination, "session-123-agent-feedback.zip").isFile)
            }
        } finally {
            project.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnexpectedApplicationPackage() {
        val project = Files.createTempDirectory("snoop-bridge-package").toFile()
        try {
            withServer(project, "com.expected.debug") { port ->
                val response = request(
                    port,
                    JSONObject()
                        .put("operation", "status")
                        .put("packageName", "com.other.debug"),
                )
                assertFalse(response.getBoolean("ok"))
                assertTrue(response.getString("message").contains("SNOOP_PACKAGE"))
            }
        } finally {
            project.deleteRecursively()
        }
    }

    @Test
    fun resendingASessionDropsAttachmentsRemovedSinceTheLastTransfer() {
        val project = Files.createTempDirectory("snoop-bridge-resend").toFile()
        try {
            withServer(project, "com.example.debug") { port ->
                export(
                    port,
                    zip(
                        "feedback.md" to "# First".toByteArray(),
                        "attachments/removed.png" to byteArrayOf(9),
                    ),
                )
                export(
                    port,
                    zip(
                        "feedback.md" to "# Second".toByteArray(),
                        "attachments/kept.png" to byteArrayOf(7),
                    ),
                )

                val destination = exportDirectory(project, "com.example.debug", "123", "full")
                assertEquals("# Second", File(destination, "feedback.md").readText())
                assertTrue(File(destination, "attachments/kept.png").isFile)
                assertFalse(File(destination, "attachments/removed.png").exists())
            }
        } finally {
            project.deleteRecursively()
        }
    }

    @Test
    fun partialExportIsFiledUnderItsPinsAndLeavesTheFullExportAlone() {
        val project = Files.createTempDirectory("snoop-bridge-selection").toFile()
        try {
            withServer(project, "com.example.debug") { port ->
                export(port, zip("feedback.md" to "# Whole session".toByteArray()))
                val archive = zip("feedback.md" to "# Only pins 2, 5 and 7".toByteArray())
                val partial = request(
                    port,
                    JSONObject()
                        .put("operation", "export")
                        .put("packageName", "com.example.debug")
                        .put("sessionId", "session-123")
                        .put("selection", "pin2-5-7")
                        .put("fileName", "session-123-pin2-5-7-agent-feedback.zip")
                        .put("contentLength", archive.size),
                    archive,
                )

                assertTrue(partial.getBoolean("ok"))
                val full = exportDirectory(project, "com.example.debug", "123", "full")
                val selected = exportDirectory(project, "com.example.debug", "123", "pin2-5-7")
                assertEquals(
                    "# Only pins 2, 5 and 7",
                    File(selected, "feedback.md").readText(),
                )
                // The partial export must not have replaced the full one.
                assertEquals("# Whole session", File(full, "feedback.md").readText())
            }
        } finally {
            project.deleteRecursively()
        }
    }

    private fun exportDirectory(
        project: File,
        packageName: String,
        shortSessionId: String,
        selection: String,
    ): File = File(project, "agent-feedback")
        .resolve(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
        .resolve(packageName)
        .resolve(shortSessionId)
        .resolve(selection)

    private fun export(port: Int, archive: ByteArray) {
        val response = request(
            port,
            JSONObject()
                .put("operation", "export")
                .put("packageName", "com.example.debug")
                .put("sessionId", "session-123")
                .put("fileName", "session-123-agent-feedback.zip")
                .put("contentLength", archive.size),
            archive,
        )
        assertTrue(response.getBoolean("ok"), response.optString("message"))
    }

    private fun withServer(
        project: File,
        expectedPackage: String,
        block: (Int) -> Unit,
    ) {
        DesktopBridgeServer(project, expectedPackage, 0).use { server ->
            val serverThread = thread(isDaemon = true) { server.serve() }
            try {
                block(server.boundPort)
            } finally {
                server.close()
                serverThread.join(2_000)
            }
        }
    }

    private fun request(
        port: Int,
        header: JSONObject,
        payload: ByteArray = byteArrayOf(),
    ): JSONObject {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
            socket.soTimeout = 5_000
            val output = BufferedOutputStream(socket.getOutputStream())
            output.write(header.toString().toByteArray())
            output.write('\n'.code)
            output.write(payload)
            output.flush()
            return JSONObject(readLine(BufferedInputStream(socket.getInputStream())))
        }
    }

    private fun readLine(input: BufferedInputStream): String {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            check(value != -1) { "Server closed without a response" }
            if (value == '\n'.code) return bytes.toString(Charsets.UTF_8.name())
            bytes.write(value)
        }
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
}
