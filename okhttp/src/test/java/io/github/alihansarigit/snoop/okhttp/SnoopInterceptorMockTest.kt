package io.github.alihansarigit.snoop.okhttp

import io.github.alihansarigit.snoop.Snoop
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoopInterceptorMockTest {
    @After
    fun tearDown() {
        Snoop.clearNetworkMocks()
        Snoop.clear()
    }

    @Test
    fun mockReturnsWithoutTouchingNetworkAndIsCaptured() {
        Snoop.addNetworkMock(
            method = "GET",
            urlPattern = "https://unreachable.invalid/profile",
            statusCode = 503,
            body = """{"offline":true}""",
        )
        val client = OkHttpClient.Builder().addInterceptor(SnoopInterceptor()).build()

        val response = client.newCall(
            Request.Builder().url("https://unreachable.invalid/profile").build(),
        ).execute()

        response.use {
            assertEquals(503, it.code)
            assertEquals("""{"offline":true}""", it.body.string())
        }
        val transaction = Snoop.store.transactions.value.single()
        assertTrue(transaction.mocked)
        assertEquals(503, transaction.responseCode)
    }
}
