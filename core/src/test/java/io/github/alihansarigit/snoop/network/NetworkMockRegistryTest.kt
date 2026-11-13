package io.github.alihansarigit.snoop.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkMockRegistryTest {
    @After
    fun tearDown() = NetworkMockRegistry.clear()

    @Test
    fun matchesMethodAndSafeWildcard() {
        NetworkMockRegistry.add(
            method = "GET",
            urlPattern = "https://api.example.com/users/*",
            statusCode = 500,
            body = """{"error":true}""",
            headers = emptyMap(),
        )

        assertEquals(
            500,
            NetworkMockRegistry.match("GET", "https://api.example.com/users/42")?.statusCode,
        )
        assertNull(NetworkMockRegistry.match("POST", "https://api.example.com/users/42"))
        assertNull(NetworkMockRegistry.match("GET", "https://evil.example/users/42"))
    }
}
