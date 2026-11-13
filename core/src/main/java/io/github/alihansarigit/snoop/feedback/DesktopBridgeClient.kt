package io.github.alihansarigit.snoop.feedback

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

internal data class DesktopBridgeConnection(
    val connected: Boolean,
    val projectName: String = "",
    val projectPath: String = "",
    val message: String = "",
)

internal data class DesktopBridgeExport(
    val projectName: String,
    val projectPath: String,
    val exportPath: String,
)

internal object DesktopBridgeClient {
    fun status(packageName: String, port: Int): DesktopBridgeConnection =
        runCatching {
            request(
                port = port,
                header = JSONObject()
                    .put("operation", "status")
                    .put("packageName", packageName),
            ).let { response ->
                val daemonProtocol = response.optInt("protocolVersion", 0)
                when {
                    !response.optBoolean("ok") -> DesktopBridgeConnection(
                        connected = false,
                        message = response.optString("message", "Desktop bridge rejected the connection"),
                    )
                    daemonProtocol != PROTOCOL_VERSION -> DesktopBridgeConnection(
                        connected = false,
                        message = "Snoop daemon speaks protocol v$daemonProtocol, " +
                            "this app expects v$PROTOCOL_VERSION — update the daemon",
                    )
                    else -> DesktopBridgeConnection(
                        connected = true,
                        projectName = response.optString("projectName"),
                        projectPath = response.optString("projectPath"),
                    )
                }
            }
        }.getOrElse {
            DesktopBridgeConnection(
                connected = false,
                message = "Desktop bridge is not reachable through ADB",
            )
        }

    /**
     * @param selection names the pins a partial export carries, e.g. `pin2-5-7`. Empty
     *   means the whole session, which the daemon files under `full`.
     */
    fun export(
        packageName: String,
        port: Int,
        sessionId: String,
        archive: File,
        selection: String = "",
    ): DesktopBridgeExport {
        require(archive.isFile) { "Agent bundle is missing" }
        val response = request(
            port = port,
            header = JSONObject()
                .put("operation", "export")
                .put("packageName", packageName)
                .put("sessionId", sessionId)
                .put("selection", selection)
                .put("fileName", archive.name)
                .put("contentLength", archive.length()),
            payload = archive,
        )
        require(response.optBoolean("ok")) {
            response.optString("message", "Desktop export failed")
        }
        return DesktopBridgeExport(
            projectName = response.optString("projectName"),
            projectPath = response.optString("projectPath"),
            exportPath = response.optString("exportPath"),
        )
    }

    private fun request(
        port: Int,
        header: JSONObject,
        payload: File? = null,
    ): JSONObject {
        require(port in 1..65535) { "Invalid desktop bridge port" }
        Socket().use { socket ->
            socket.connect(
                InetSocketAddress(LOOPBACK_ADDRESS, port),
                CONNECT_TIMEOUT_MILLIS,
            )
            socket.soTimeout = RESPONSE_TIMEOUT_MILLIS
            val output = BufferedOutputStream(socket.getOutputStream())
            output.write(header.toString().toByteArray(Charsets.UTF_8))
            output.write('\n'.code)
            payload?.inputStream()?.buffered()?.use { it.copyTo(output) }
            output.flush()
            return JSONObject(readLine(BufferedInputStream(socket.getInputStream())))
        }
    }

    private fun readLine(input: BufferedInputStream): String {
        val bytes = ArrayList<Byte>()
        while (bytes.size < MAX_RESPONSE_BYTES) {
            val value = input.read()
            if (value == -1) throw EOFException("Desktop bridge closed without a response")
            if (value == '\n'.code) return bytes.toByteArray().toString(Charsets.UTF_8)
            bytes += value.toByte()
        }
        error("Desktop bridge response is too large")
    }

    /** Must match `DesktopBridgeServer.PROTOCOL_VERSION` in the daemon. */
    private const val PROTOCOL_VERSION = 2
    private const val LOOPBACK_ADDRESS = "127.0.0.1"
    private const val CONNECT_TIMEOUT_MILLIS = 1_500
    private const val RESPONSE_TIMEOUT_MILLIS = 30_000
    private const val MAX_RESPONSE_BYTES = 16 * 1024
}
