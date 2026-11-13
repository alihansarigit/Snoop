package io.github.alihansarigit.snoop.network

import androidx.annotation.RestrictTo
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class NetworkMockRule(
    val id: String,
    val method: String,
    val urlPattern: String,
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String>,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object NetworkMockRegistry {
    private val rules = ConcurrentHashMap<String, NetworkMockRule>()

    fun add(
        method: String,
        urlPattern: String,
        statusCode: Int,
        body: String,
        headers: Map<String, String>,
    ): String {
        require(urlPattern.isNotBlank()) { "urlPattern cannot be blank" }
        require(statusCode in 100..599) { "statusCode must be between 100 and 599" }
        val id = UUID.randomUUID().toString()
        rules[id] = NetworkMockRule(
            id = id,
            method = method.trim().uppercase().ifEmpty { "*" }.take(20),
            urlPattern = urlPattern.trim().take(MAX_PATTERN_LENGTH),
            statusCode = statusCode,
            body = body.take(MAX_BODY_LENGTH),
            headers = headers
                .filterKeys { it.length <= 100 }
                .mapValues { (_, value) -> value.take(2_000) },
        )
        return id
    }

    fun remove(id: String): Boolean = rules.remove(id) != null

    fun clear() = rules.clear()

    fun snapshot(): List<NetworkMockRule> = rules.values.sortedBy { it.urlPattern }

    fun match(method: String, url: String): NetworkMockRule? = rules.values.firstOrNull { rule ->
        (rule.method == "*" || rule.method.equals(method, ignoreCase = true)) &&
            globMatches(rule.urlPattern, url)
    }

    private fun globMatches(pattern: String, value: String): Boolean {
        if ('*' !in pattern) return value == pattern
        val regex = pattern.split('*').joinToString(".*") { Regex.escape(it) }
        return runCatching { Regex("^$regex$").matches(value) }.getOrDefault(false)
    }

    private const val MAX_PATTERN_LENGTH = 1_000
    private const val MAX_BODY_LENGTH = 1_000_000
}
