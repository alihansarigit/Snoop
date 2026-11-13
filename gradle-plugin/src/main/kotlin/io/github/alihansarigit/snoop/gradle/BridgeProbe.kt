package io.github.alihansarigit.snoop.gradle

import java.io.BufferedInputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** What a `status` handshake told us about whoever already holds the bridge port. */
internal sealed interface BridgeProbeResult {

    /** Nothing is listening; the port is ours to take. */
    object Free : BridgeProbeResult

    /** A bridge is serving this very project. Starting another one would be a no-op. */
    data class OwnedByThisProject(val projectPath: String) : BridgeProbeResult

    /**
     * Something answered but is not our bridge — most often the same daemon started
     * from a different project. Silently doing nothing here is what would send
     * feedback to the wrong repository, so the caller must report it.
     */
    data class Foreign(val description: String) : BridgeProbeResult
}

internal object BridgeProbe {

    fun inspect(port: Int, projectDirectory: File, packageName: String): BridgeProbeResult {
        val response = request(port, packageName) ?: return BridgeProbeResult.Free
        if (!response.boolean("ok")) {
            return BridgeProbeResult.Foreign(
                response.string("message").ifBlank { "the daemon rejected the handshake" },
            )
        }
        val reported = response.string("projectPath")
        val expected = runCatching { projectDirectory.canonicalPath }
            .getOrDefault(projectDirectory.absolutePath)
        val actual = runCatching { File(reported).canonicalPath }.getOrDefault(reported)
        return if (actual == expected) {
            BridgeProbeResult.OwnedByThisProject(reported)
        } else {
            BridgeProbeResult.Foreign("it is serving $reported")
        }
    }

    private fun request(port: Int, packageName: String): Response? = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(LOOPBACK_ADDRESS, port), CONNECT_TIMEOUT_MILLIS)
            socket.soTimeout = RESPONSE_TIMEOUT_MILLIS
            val header = """{"operation":"status","packageName":"$packageName"}"""
            socket.getOutputStream().apply {
                write(header.toByteArray(Charsets.UTF_8))
                write('\n'.code)
                flush()
            }
            Response(readLine(BufferedInputStream(socket.getInputStream())))
        }
    }.getOrNull()

    private fun readLine(input: BufferedInputStream): String {
        val bytes = ArrayList<Byte>()
        while (bytes.size < MAX_RESPONSE_BYTES) {
            val value = input.read()
            if (value == -1) break
            if (value == '\n'.code) break
            bytes += value.toByte()
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    /**
     * The handshake has three flat fields and the plugin classpath is shared with every
     * other plugin in the build, so this reads them directly instead of pulling in a
     * JSON library that could collide.
     */
    private class Response(private val raw: String) {
        fun boolean(field: String): Boolean =
            Regex("\"$field\"\\s*:\\s*(true|false)").find(raw)?.groupValues?.get(1) == "true"

        fun string(field: String): String =
            Regex("\"$field\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.replace("\\/", "/")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                .orEmpty()
    }

    private const val LOOPBACK_ADDRESS = "127.0.0.1"
    private const val CONNECT_TIMEOUT_MILLIS = 700
    private const val RESPONSE_TIMEOUT_MILLIS = 3_000
    private const val MAX_RESPONSE_BYTES = 16 * 1024
}
