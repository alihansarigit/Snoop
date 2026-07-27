package io.github.alihansarigit.snoop.store

import io.github.alihansarigit.snoop.model.NetworkTransaction
import io.github.alihansarigit.snoop.model.TransactionStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON (de)serialization for [NetworkTransaction], used by [DiskPersistence]. Kept
 * deliberately simple (org.json, no third-party serializer) so `:core` stays lean.
 * Headers are stored as `[name, value]` pairs; nullable fields are omitted when absent.
 */
internal fun NetworkTransaction.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("method", method)
    put("url", url)
    put("host", host)
    put("path", path)
    put("requestHeaders", headersToJson(requestHeaders))
    requestBody?.let { put("requestBody", it) }
    requestContentType?.let { put("requestContentType", it) }
    put("requestBodySize", requestBodySize)
    responseCode?.let { put("responseCode", it) }
    responseMessage?.let { put("responseMessage", it) }
    put("responseHeaders", headersToJson(responseHeaders))
    responseBody?.let { put("responseBody", it) }
    responseContentType?.let { put("responseContentType", it) }
    put("responseBodySize", responseBodySize)
    put("status", status.name)
    error?.let { put("error", it) }
    put("requestDate", requestDate)
    durationMs?.let { put("durationMs", it) }
}

/** Reconstruct a transaction, or `null` if the object is malformed. */
internal fun JSONObject.toTransaction(): NetworkTransaction? =
    try {
        NetworkTransaction(
            id = getLong("id"),
            method = optString("method"),
            url = optString("url"),
            host = optString("host"),
            path = optString("path"),
            requestHeaders = optJSONArray("requestHeaders")?.toHeaders() ?: emptyList(),
            requestBody = optStringOrNull("requestBody"),
            requestContentType = optStringOrNull("requestContentType"),
            requestBodySize = optLong("requestBodySize"),
            responseCode = if (has("responseCode")) getInt("responseCode") else null,
            responseMessage = optStringOrNull("responseMessage"),
            responseHeaders = optJSONArray("responseHeaders")?.toHeaders() ?: emptyList(),
            responseBody = optStringOrNull("responseBody"),
            responseContentType = optStringOrNull("responseContentType"),
            responseBodySize = optLong("responseBodySize"),
            status = runCatching { TransactionStatus.valueOf(getString("status")) }
                .getOrDefault(TransactionStatus.COMPLETE),
            error = optStringOrNull("error"),
            requestDate = optLong("requestDate"),
            durationMs = if (has("durationMs")) getLong("durationMs") else null,
        )
    } catch (_: Exception) {
        null
    }

private fun headersToJson(headers: List<Pair<String, String>>): JSONArray {
    val array = JSONArray()
    headers.forEach { (name, value) -> array.put(JSONArray().put(name).put(value)) }
    return array
}

private fun JSONArray.toHeaders(): List<Pair<String, String>> =
    (0 until length()).mapNotNull { index ->
        val pair = optJSONArray(index) ?: return@mapNotNull null
        if (pair.length() < 2) null else pair.optString(0) to pair.optString(1)
    }

private fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null
