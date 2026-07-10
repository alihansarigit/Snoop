package io.github.alihansarigit.snoop.util

import io.github.alihansarigit.snoop.model.NetworkTransaction
import io.github.alihansarigit.snoop.model.TransactionStatus
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HAR_VERSION = "1.2"
private const val CREATOR_NAME = "Snoop"

// Kept in sync with gradle.properties `VERSION_NAME`. Cosmetic only (HAR `creator.version`).
private const val SNOOP_VERSION = "0.2.0"

// Real wire protocol isn't captured in the model; HAR requires a non-empty string here.
private const val HTTP_VERSION = "HTTP/1.1"

/**
 * Serialize [entries] as a [HAR 1.2](http://www.softwareishard.com/blog/har-12-spec/)
 * (HTTP Archive) document — a pretty-printed JSON string that imports into Chrome
 * DevTools, Charles, Proxyman, Postman, and other tooling.
 *
 * All required members of the spec are emitted; unknown values use the spec's `-1`
 * ("not available") sentinel. Bodies are whatever the store holds — so redaction has
 * already been applied. `httpVersion` is reported as `HTTP/1.1` because the adapters
 * do not capture the negotiated protocol.
 */
internal fun harExport(entries: List<NetworkTransaction>): String {
    val log = JSONObject().apply {
        put("version", HAR_VERSION)
        put(
            "creator",
            JSONObject().apply {
                put("name", CREATOR_NAME)
                put("version", SNOOP_VERSION)
            },
        )
        val arr = JSONArray()
        entries.forEach { arr.put(it.toHarEntry()) }
        put("entries", arr)
    }
    return JSONObject().put("log", log).toString(2)
}

private fun NetworkTransaction.toHarEntry(): JSONObject {
    val time = durationMs?.coerceAtLeast(0L) ?: 0L
    return JSONObject().apply {
        put("startedDateTime", iso8601(requestDate))
        put("time", time)
        put("request", harRequest())
        put("response", harResponse())
        put("cache", JSONObject())
        put(
            "timings",
            JSONObject().apply {
                put("send", 0)
                put("wait", time)
                put("receive", 0)
            },
        )
        // Non-standard extensions (HAR permits leading-underscore members).
        put("_id", id)
        if (status == TransactionStatus.FAILED) put("_error", error ?: "failed")
    }
}

private fun NetworkTransaction.harRequest(): JSONObject = JSONObject().apply {
    put("method", method)
    put("url", url)
    put("httpVersion", HTTP_VERSION)
    put("cookies", JSONArray())
    put("headers", headersToJson(requestHeaders))
    put("queryString", queryStringToJson(url))
    if (requestBody != null) {
        put(
            "postData",
            JSONObject().apply {
                put("mimeType", requestContentType ?: "")
                put("text", requestBody)
            },
        )
    }
    put("headersSize", -1)
    put("bodySize", if (requestBodySize > 0) requestBodySize else -1)
}

private fun NetworkTransaction.harResponse(): JSONObject = JSONObject().apply {
    put("status", responseCode ?: 0)
    put("statusText", responseMessage ?: "")
    put("httpVersion", HTTP_VERSION)
    put("cookies", JSONArray())
    put("headers", headersToJson(responseHeaders))
    put(
        "content",
        JSONObject().apply {
            put("size", if (responseBodySize >= 0) responseBodySize else (responseBody?.length?.toLong() ?: 0L))
            put("mimeType", responseContentType ?: "")
            if (responseBody != null) put("text", responseBody)
        },
    )
    put("redirectURL", responseHeaders.firstOrNull { it.first.equals("location", ignoreCase = true) }?.second ?: "")
    put("headersSize", -1)
    put("bodySize", if (responseBodySize >= 0) responseBodySize else -1)
}

private fun headersToJson(headers: List<Pair<String, String>>): JSONArray {
    val arr = JSONArray()
    headers.forEach { (name, value) ->
        arr.put(JSONObject().apply {
            put("name", name)
            put("value", value)
        })
    }
    return arr
}

private fun queryStringToJson(url: String): JSONArray {
    val arr = JSONArray()
    val query = url.substringAfter('?', "")
    if (query.isEmpty()) return arr
    query.split('&').forEach { pair ->
        if (pair.isEmpty()) return@forEach
        arr.put(JSONObject().apply {
            put("name", urlDecode(pair.substringBefore('=')))
            put("value", urlDecode(pair.substringAfter('=', "")))
        })
    }
    return arr
}

private fun urlDecode(value: String): String =
    try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }

private fun iso8601(epochMillis: Long): String {
    // SimpleDateFormat isn't thread-safe; export is rare, so a fresh instance is fine.
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    val millis = if (epochMillis > 0) epochMillis else System.currentTimeMillis()
    return format.format(Date(millis))
}
