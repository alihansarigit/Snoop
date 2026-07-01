package io.github.alihansarigit.snoop.util

import io.github.alihansarigit.snoop.model.NetworkTransaction

/** Single-quote escaping for shell. */
private fun String.shellEscape(): String = replace("'", "'\\''")

/** A copy-pasteable cURL command reconstructed from the captured request. */
internal fun NetworkTransaction.toCurl(): String = buildString {
    append("curl -X ").append(method).append(" '").append(url).append("'")
    requestHeaders.forEach { (name, value) ->
        append(" \\\n  -H '").append(name).append(": ").append(value.shellEscape()).append("'")
    }
    if (!requestBody.isNullOrBlank()) {
        append(" \\\n  --data '").append(requestBody.shellEscape()).append("'")
    }
}

/** Full request+response dump of a single transaction. */
internal fun NetworkTransaction.fullDump(): String = buildString {
    append(method).append(' ').append(url)
    if (responseCode != null) append("  → ").append(responseCode)
    append("   @").append(formatTime(requestDate))
    durationMs?.let { append("  (").append(formatDuration(it)).append(")") }
    append("\n\n--- REQUEST HEADERS ---\n")
    append(requestHeaders.joinToString("\n") { "${it.first}: ${it.second}" }.ifBlank { "(none)" })
    append("\n\n--- REQUEST BODY ---\n")
    append(requestBody?.ifBlank { null } ?: "(no request body)")
    append("\n\n--- RESPONSE BODY ---\n")
    append(responseBody?.ifBlank { null } ?: "(no response body)")
}

/** Dump of every (filtered) transaction, newest first. */
internal fun allLogsDump(entries: List<NetworkTransaction>): String = buildString {
    append("# Snoop network logs (").append(entries.size).append(" entries)\n\n")
    entries.forEachIndexed { index, entry ->
        append("===== [").append(index + 1).append('/').append(entries.size).append("] =====\n")
        append(entry.fullDump())
        append("\n\n")
    }
}
