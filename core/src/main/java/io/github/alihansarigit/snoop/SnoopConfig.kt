package io.github.alihansarigit.snoop

import io.github.alihansarigit.snoop.model.NetworkTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Central, mutable runtime configuration for Snoop. Reached via [Snoop.config].
 *
 * Settings are read live at capture time, so changing them affects transactions
 * captured afterwards. Every accessor is safe to call from any thread.
 *
 * Today this hosts **redaction** — masking sensitive header values and JSON body
 * fields so tokens and cookies never reach the inspector, the clipboard dumps, or
 * an exported `.har`. Redaction runs once, centrally, in [NetworkLogStore][io.github.alihansarigit.snoop.store.NetworkLogStore],
 * so it covers OkHttp and Ktor alike.
 */
class SnoopConfig internal constructor() {

    private val headerLock = Any()
    private val bodyKeyLock = Any()

    /** Header names (compared case-insensitively) whose values are masked. */
    private val redactedHeaders = linkedSetOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
    )

    /** JSON body keys (compared case-insensitively) whose values are masked, at any depth. */
    private val redactedBodyKeys = linkedSetOf<String>()

    /** Master switch. When `false`, transactions are stored verbatim. Default `true`. */
    @Volatile
    var redactionEnabled: Boolean = true

    /** Text substituted for every redacted value. Default `[REDACTED]`. */
    @Volatile
    var redactionMask: String = DEFAULT_MASK

    /**
     * When `true`, compressed response bodies (`gzip`, `deflate`) are decompressed to
     * readable text at capture time. `br` (Brotli) is not decoded. Default `true`.
     */
    @Volatile
    var decodeBodies: Boolean = true

    /** Invoked when [persistenceEnabled] flips; wired by `Snoop` to start/stop disk I/O. */
    internal var onPersistenceToggled: ((Boolean) -> Unit)? = null

    /**
     * Persist captured transactions to app-private storage and restore them on the next
     * launch. Default `false` — enable explicitly, since it writes captured traffic to
     * disk. Redaction is applied before storage, so masked values are what get written.
     */
    var persistenceEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            onPersistenceToggled?.invoke(value)
        }

    /**
     * On load and on write, drop persisted transactions older than this many milliseconds.
     * `0` (default) imposes no age limit; count is still bounded by the in-memory ring
     * buffer. Example: `Snoop.config.maxAgeMillis = 24 * 60 * 60 * 1000L`.
     */
    @Volatile
    var maxAgeMillis: Long = 0L

    /**
     * Add header names to the redaction set (case-insensitive). `Authorization`,
     * `Proxy-Authorization`, `Cookie`, `Set-Cookie` and `X-Api-Key` are covered by
     * default. Returns `this` for chaining.
     */
    fun redactHeaders(vararg names: String): SnoopConfig = apply {
        synchronized(headerLock) { names.forEach { redactedHeaders.add(it.lowercase(Locale.ROOT)) } }
    }

    /**
     * Add JSON body keys to mask (case-insensitive), matched at every nesting level —
     * e.g. `redactBodyKeys("password", "token")`. Non-JSON bodies are left untouched.
     * Returns `this` for chaining.
     */
    fun redactBodyKeys(vararg keys: String): SnoopConfig = apply {
        synchronized(bodyKeyLock) { keys.forEach { redactedBodyKeys.add(it.lowercase(Locale.ROOT)) } }
    }

    /** Remove a header from the redaction set — e.g. to un-mask a default. */
    fun unredactHeader(name: String): SnoopConfig = apply {
        synchronized(headerLock) { redactedHeaders.remove(name.lowercase(Locale.ROOT)) }
    }

    /**
     * Return a copy of [tx] with configured headers and body keys masked. Idempotent:
     * re-masking an already-masked value is a no-op, so it is safe to run on every store
     * write. Returns [tx] unchanged when redaction is disabled or nothing matches.
     */
    internal fun redact(tx: NetworkTransaction): NetworkTransaction {
        if (!redactionEnabled) return tx
        val headerSet = synchronized(headerLock) { redactedHeaders.toSet() }
        val bodyKeys = synchronized(bodyKeyLock) { redactedBodyKeys.toSet() }
        if (headerSet.isEmpty() && bodyKeys.isEmpty()) return tx
        return tx.copy(
            requestHeaders = maskHeaders(tx.requestHeaders, headerSet),
            responseHeaders = maskHeaders(tx.responseHeaders, headerSet),
            requestBody = maskBody(tx.requestBody, bodyKeys),
            responseBody = maskBody(tx.responseBody, bodyKeys),
        )
    }

    private fun maskHeaders(
        headers: List<Pair<String, String>>,
        names: Set<String>,
    ): List<Pair<String, String>> {
        if (headers.isEmpty() || names.isEmpty()) return headers
        var changed = false
        val masked = headers.map { (name, value) ->
            if (names.contains(name.lowercase(Locale.ROOT))) {
                changed = true
                name to redactionMask
            } else {
                name to value
            }
        }
        return if (changed) masked else headers
    }

    private fun maskBody(body: String?, keys: Set<String>): String? {
        if (body.isNullOrBlank() || keys.isEmpty()) return body
        val trimmed = body.trimStart()
        return try {
            when {
                trimmed.startsWith("{") -> maskObject(JSONObject(body), keys).toString()
                trimmed.startsWith("[") -> maskArray(JSONArray(body), keys).toString()
                else -> body
            }
        } catch (_: Exception) {
            body
        }
    }

    private fun maskObject(obj: JSONObject, keys: Set<String>): JSONObject {
        // Snapshot names first: put() mutates the object while we iterate.
        for (name in obj.keys().asSequence().toList()) {
            if (keys.contains(name.lowercase(Locale.ROOT))) {
                obj.put(name, redactionMask)
            } else {
                when (val value = obj.get(name)) {
                    is JSONObject -> maskObject(value, keys)
                    is JSONArray -> maskArray(value, keys)
                }
            }
        }
        return obj
    }

    private fun maskArray(arr: JSONArray, keys: Set<String>): JSONArray {
        for (i in 0 until arr.length()) {
            when (val value = arr.get(i)) {
                is JSONObject -> maskObject(value, keys)
                is JSONArray -> maskArray(value, keys)
            }
        }
        return arr
    }

    private companion object {
        const val DEFAULT_MASK = "[REDACTED]"
    }
}
