package io.github.alihansarigit.snoop.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

class SnoopMcpServerTest {
    private val server = SnoopMcpServer()

    @Test
    fun initializeDeclaresToolsCapability() {
        val response = server.handle(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "initialize")
                .put("params", JSONObject()),
        )!!

        assertEquals("2.0", response.getString("jsonrpc"))
        assertTrue(response.getJSONObject("result").getJSONObject("capabilities").has("tools"))
    }

    @Test
    fun toolsAreListedDeterministically() {
        val response = server.handle(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 2)
                .put("method", "tools/list"),
        )!!
        val tools = response.getJSONObject("result").getJSONArray("tools")

        assertEquals("snoop_list_sessions", tools.getJSONObject(0).getString("name"))
        assertEquals("snoop_get_session", tools.getJSONObject(1).getString("name"))
        assertEquals("snoop_pull_session", tools.getJSONObject(2).getString("name"))
    }

    @Test
    fun notificationsDoNotProduceResponses() {
        val response = server.handle(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/initialized"),
        )
        assertEquals(null, response)
    }
}
