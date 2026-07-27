package io.github.alihansarigit.snoop.util

import androidx.annotation.RestrictTo
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Decompress a captured body according to its `Content-Encoding`.
 *
 * Supports `gzip` (and `x-gzip`) and `deflate` (both zlib-wrapped and raw). `identity`
 * and blank pass through; unsupported encodings such as `br` (Brotli) are returned
 * unchanged. Every failure falls back to the input bytes, so a truncated, malformed,
 * or already-decoded stream never throws — decoding plain text as gzip simply fails
 * the magic-byte check and returns the original bytes.
 *
 * Public only so the capture adapters (`:okhttp`, `:ktor` — separate Gradle modules)
 * can reach it; it is not part of the app-facing API.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun decodeBody(bytes: ByteArray, contentEncoding: String?): ByteArray {
    if (bytes.isEmpty()) return bytes
    return when (contentEncoding?.trim()?.lowercase(Locale.ROOT)) {
        null, "", "identity" -> bytes
        "gzip", "x-gzip" -> gunzip(bytes) ?: bytes
        "deflate" -> inflate(bytes) ?: bytes
        else -> bytes
    }
}

private fun gunzip(bytes: ByteArray): ByteArray? =
    try {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

/** Try zlib-wrapped deflate first, then headerless (raw) deflate. */
private fun inflate(bytes: ByteArray): ByteArray? =
    inflateWith(bytes, nowrap = false) ?: inflateWith(bytes, nowrap = true)

private fun inflateWith(bytes: ByteArray, nowrap: Boolean): ByteArray? {
    val inflater = Inflater(nowrap)
    return try {
        InflaterInputStream(ByteArrayInputStream(bytes), inflater).use { it.readBytes() }
    } catch (_: Exception) {
        null
    } finally {
        inflater.end()
    }
}
