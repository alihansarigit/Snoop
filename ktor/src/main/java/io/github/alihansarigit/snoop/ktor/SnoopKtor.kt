package io.github.alihansarigit.snoop.ktor

import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.model.NetworkTransaction
import io.github.alihansarigit.snoop.model.TransactionStatus
import io.github.alihansarigit.snoop.util.decodeBody
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.call.save
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import java.nio.charset.Charset
import kotlin.coroutines.cancellation.CancellationException

/** Configuration for [SnoopKtor]. */
class SnoopKtorConfig {
    /** Request/response bodies larger than this (in bytes) are truncated when captured. */
    var maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES
}

/**
 * Ktor client plugin that captures every request/response into Snoop — the Ktor
 * counterpart of `SnoopInterceptor` for OkHttp.
 *
 * ```
 * val client = HttpClient(engine) {
 *     install(SnoopKtor)
 *     // or, to change the body cap:
 *     install(SnoopKtor) { maxBodyBytes = 500_000 }
 * }
 * ```
 *
 * The response body is captured by [saving][save] the call, which buffers it in
 * memory so Snoop and your app can both read it — the caller's stream is never
 * consumed. Server-Sent Event streams are left untouched (metadata captured only).
 */
val SnoopKtor = createClientPlugin("SnoopKtor", ::SnoopKtorConfig) {
    val maxBodyBytes = pluginConfig.maxBodyBytes

    on(Send) { request ->
        val id = Snoop.store.nextId()
        val url = request.url.build()
        val outgoing = request.body as? OutgoingContent
        val (requestBody, requestSize) = readRequestBody(outgoing, maxBodyBytes)

        Snoop.store.put(
            NetworkTransaction(
                id = id,
                method = request.method.value,
                url = url.toString(),
                host = url.host,
                path = url.encodedPath,
                requestHeaders = request.headers.entries()
                    .flatMap { (name, values) -> values.map { name to it } },
                requestBody = requestBody,
                requestContentType = outgoing?.contentType?.toString(),
                requestBodySize = requestSize,
                status = TransactionStatus.PENDING,
                requestDate = System.currentTimeMillis(),
            ),
        )

        val startNs = System.nanoTime()
        val call = try {
            proceed(request)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Snoop.store.update(id) {
                it.copy(
                    status = TransactionStatus.FAILED,
                    error = error.message ?: error::class.simpleName,
                    durationMs = elapsedMs(startNs),
                )
            }
            throw error
        }

        captureResponse(id, call, maxBodyBytes, startNs)
    }
}

/**
 * Records the response for transaction [id] and returns the call to pass back down
 * the pipeline. For everything but SSE, the call is [saved][save] first so the body
 * can be read for capture without stealing it from the caller.
 */
private suspend fun captureResponse(
    id: Long,
    call: HttpClientCall,
    maxBodyBytes: Long,
    startNs: Long,
): HttpClientCall {
    val isEventStream =
        call.response.contentType()?.withoutParameters() == ContentType.Text.EventStream
    if (isEventStream) {
        recordResponse(id, call.response, body = null, size = call.response.contentLength() ?: -1L, startNs)
        return call
    }

    val saved = try {
        call.save()
    } catch (_: Throwable) {
        call
    }
    val response = saved.response
    val bytes = try {
        response.body<ByteArray>()
    } catch (_: Throwable) {
        null
    }
    val decoded = bytes?.let {
        if (Snoop.config.decodeBodies) decodeBody(it, response.headers["Content-Encoding"]) else it
    }
    val body = decoded?.let { decodeBounded(it, response.charset(), maxBodyBytes) }
    val size = bytes?.size?.toLong() ?: (response.contentLength() ?: -1L)
    recordResponse(id, response, body, size, startNs)
    return saved
}

private fun recordResponse(
    id: Long,
    response: HttpResponse,
    body: String?,
    size: Long,
    startNs: Long,
) {
    Snoop.store.update(id) {
        it.copy(
            responseCode = response.status.value,
            responseMessage = response.status.description,
            responseHeaders = response.headers.entries()
                .flatMap { (name, values) -> values.map { name to it } },
            responseBody = body,
            responseContentType = response.contentType()?.toString(),
            responseBodySize = size,
            status = TransactionStatus.COMPLETE,
            durationMs = elapsedMs(startNs),
        )
    }
}

/**
 * Reads an in-memory outgoing body (text / byte array — the common JSON case).
 * Streaming bodies (channel content) are not drained, as that would corrupt the
 * request; only their declared size is recorded.
 */
private fun readRequestBody(content: OutgoingContent?, maxBodyBytes: Long): Pair<String?, Long> =
    when (content) {
        null, is OutgoingContent.NoContent -> null to 0L
        is OutgoingContent.ByteArrayContent -> {
            val bytes = content.bytes()
            decodeBounded(bytes, content.contentType?.charset(), maxBodyBytes) to bytes.size.toLong()
        }
        else -> null to (content.contentLength ?: 0L)
    }

private fun decodeBounded(bytes: ByteArray, charset: Charset?, maxBodyBytes: Long): String {
    val cs = charset ?: Charsets.UTF_8
    val max = maxBodyBytes.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return if (bytes.size > max) {
        String(bytes, 0, max, cs) + "\n… (truncated)"
    } else {
        String(bytes, cs)
    }
}

private fun elapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000

private const val DEFAULT_MAX_BODY_BYTES = 250_000L
