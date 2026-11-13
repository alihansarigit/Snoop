package io.github.alihansarigit.snoop.daemon

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Minimal newline-delimited MCP stdio server. Stdout is reserved exclusively for JSON-RPC. */
internal class SnoopMcpServer(
    private val bridge: AdbSessionBridge = AdbSessionBridge(),
    private val defaultPackage: String? = System.getenv("SNOOP_PACKAGE"),
) {
    fun run() {
        generateSequence(::readLine).forEach { line ->
            if (line.isBlank()) return@forEach
            val request = runCatching { JSONObject(line) }.getOrElse {
                write(error(null, -32700, "Parse error"))
                return@forEach
            }
            handle(request)?.let(::write)
        }
    }

    internal fun handle(request: JSONObject): JSONObject? {
        val method = request.optString("method")
        val id = request.opt("id")
        if (!request.has("id")) {
            // Notifications are intentionally acknowledged by silence.
            return null
        }
        return try {
            when (method) {
                "initialize" -> result(
                    id,
                    JSONObject()
                        .put("protocolVersion", PROTOCOL_VERSION)
                        .put("capabilities", JSONObject().put("tools", JSONObject()))
                        .put(
                            "serverInfo",
                            JSONObject().put("name", "snoop-adb").put("version", SERVER_VERSION),
                        ),
                )
                "ping" -> result(id, JSONObject())
                "tools/list" -> result(id, JSONObject().put("tools", tools()))
                "tools/call" -> result(id, callTool(request.optJSONObject("params") ?: JSONObject()))
                else -> error(id, -32601, "Method not found: $method")
            }
        } catch (error: Throwable) {
            result(
                id,
                JSONObject()
                    .put("content", JSONArray().put(textContent(error.message ?: "Tool failed")))
                    .put("isError", true),
            )
        }
    }

    private fun callTool(params: JSONObject): JSONObject {
        val name = params.getString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()
        val packageName = arguments.optString("package_name").ifBlank {
            defaultPackage ?: error("package_name is required when SNOOP_PACKAGE is not configured")
        }
        val text = when (name) {
            "snoop_list_sessions" -> bridge.listSessions(packageName).toString(2)
            "snoop_get_session" -> bridge.readSession(
                packageName,
                arguments.getString("session_id"),
            )
            "snoop_pull_session" -> bridge.pullSessionArchive(
                packageName,
                arguments.getString("session_id"),
                File(arguments.getString("output_directory")).absoluteFile,
            ).absolutePath
            else -> error("Unknown tool: $name")
        }
        return JSONObject().put("content", JSONArray().put(textContent(text))).put("isError", false)
    }

    private fun tools(): JSONArray = JSONArray()
        .put(
            tool(
                name = "snoop_list_sessions",
                description = "List feedback sessions stored by a debuggable Android app connected over ADB.",
                properties = JSONObject().put(
                    "package_name",
                    JSONObject().put("type", "string").put(
                        "description",
                        "Android application id. Optional when SNOOP_PACKAGE is configured.",
                    ),
                ),
                required = emptyList(),
            ),
        )
        .put(
            tool(
                name = "snoop_get_session",
                description = "Read an AI-ready Markdown export, or the live JSON manifest for an open session.",
                properties = commonSessionProperties(),
                required = listOf("session_id"),
            ),
        )
        .put(
            tool(
                name = "snoop_pull_session",
                description = "Pull a session and all attachments into a local tar archive using ADB run-as.",
                properties = commonSessionProperties().put(
                    "output_directory",
                    JSONObject().put("type", "string").put("description", "Existing or creatable local directory."),
                ),
                required = listOf("session_id", "output_directory"),
            ),
        )

    private fun commonSessionProperties(): JSONObject = JSONObject()
        .put("package_name", JSONObject().put("type", "string"))
        .put("session_id", JSONObject().put("type", "string"))

    private fun tool(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String>,
    ): JSONObject = JSONObject()
        .put("name", name)
        .put("title", name.replace('_', ' '))
        .put("description", description)
        .put(
            "inputSchema",
            JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", JSONArray(required))
                .put("additionalProperties", false),
        )

    private fun textContent(text: String): JSONObject =
        JSONObject().put("type", "text").put("text", text)

    private fun result(id: Any?, payload: JSONObject): JSONObject =
        JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL).put("result", payload)

    private fun error(id: Any?, code: Int, message: String): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id ?: JSONObject.NULL)
            .put("error", JSONObject().put("code", code).put("message", message))

    private fun write(response: JSONObject) {
        kotlin.io.println(response.toString())
        System.out.flush()
    }

    private companion object {
        const val PROTOCOL_VERSION = "2025-11-25"
        const val SERVER_VERSION = "0.1.0"
    }
}
