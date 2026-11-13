package io.github.alihansarigit.snoop.ktor

import io.github.alihansarigit.snoop.Snoop
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoopKtorMockTest {
    @After
    fun tearDown() {
        Snoop.clearNetworkMocks()
        Snoop.clear()
    }

    @Test
    fun mockBypassesEngineAndIsCaptured() = runBlocking {
        var engineCalled = false
        val client = HttpClient(
            MockEngine {
                engineCalled = true
                error("Snoop mock should bypass the engine")
            },
        ) {
            install(SnoopKtor)
        }
        Snoop.addNetworkMock(
            method = "GET",
            urlPattern = "https://unreachable.invalid/profile",
            statusCode = 418,
            body = """{"mocked":true}""",
        )

        val response = client.get("https://unreachable.invalid/profile")

        assertEquals(418, response.status.value)
        assertEquals("""{"mocked":true}""", response.bodyAsText())
        assertTrue(!engineCalled)
        assertTrue(Snoop.store.transactions.value.single().mocked)
        client.close()
    }
}
