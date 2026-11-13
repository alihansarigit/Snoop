package io.github.alihansarigit.snoop.okhttp

import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.model.NetworkTransaction
import io.github.alihansarigit.snoop.model.TransactionStatus
import io.github.alihansarigit.snoop.network.NetworkMockRegistry
import io.github.alihansarigit.snoop.network.NetworkReplayRegistry
import io.github.alihansarigit.snoop.util.decodeBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

/**
 * Captures every request/response that flows through OkHttp into Snoop.
 *
 * ```
 * OkHttpClient.Builder()
 *     .addInterceptor(SnoopInterceptor())
 *     .build()
 * ```
 *
 * @param maxBodyBytes bodies larger than this are truncated when captured.
 */
class SnoopInterceptor @JvmOverloads constructor(
    private val maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val id = Snoop.store.nextId()
        val url = request.url
        val (requestBody, requestSize) = readRequestBody(request)
        val sessionId = Snoop.activeSessionId()

        Snoop.store.put(
            NetworkTransaction(
                id = id,
                sessionId = sessionId,
                method = request.method,
                url = url.toString(),
                host = url.host,
                path = url.encodedPath,
                requestHeaders = request.headers.map { it.first to it.second },
                requestBody = requestBody,
                requestContentType = request.body?.contentType()?.toString(),
                requestBodySize = requestSize,
                status = TransactionStatus.PENDING,
                requestDate = System.currentTimeMillis(),
            ),
        )
        registerReplay(id, chain.call())

        NetworkMockRegistry.match(request.method, url.toString())?.let { mock ->
            val responseHeaders = mock.headers.toMutableMap().apply {
                putIfAbsent("Content-Type", "application/json; charset=utf-8")
                put("X-Snoop-Mock", mock.id)
            }
            val mediaType = responseHeaders.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value
                ?.toMediaTypeOrNull()
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(mock.statusCode)
                .message("Snoop Mock")
                .apply { responseHeaders.forEach { (name, value) -> addHeader(name, value) } }
                .body(mock.body.toResponseBody(mediaType))
                .build()
            Snoop.store.update(id) {
                it.copy(
                    responseCode = mock.statusCode,
                    responseMessage = "Snoop Mock",
                    responseHeaders = responseHeaders.map { header -> header.key to header.value },
                    responseBody = mock.body,
                    responseContentType = mediaType?.toString(),
                    responseBodySize = mock.body.toByteArray().size.toLong(),
                    status = TransactionStatus.COMPLETE,
                    durationMs = 0L,
                    mocked = true,
                )
            }
            return response
        }

        val startNs = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            Snoop.store.update(id) {
                it.copy(
                    status = TransactionStatus.FAILED,
                    error = e.message ?: e.javaClass.simpleName,
                    durationMs = elapsedMs(startNs),
                )
            }
            throw e
        }

        val responseBody = try {
            val peeked = response.peekBody(maxBodyBytes)
            val charset = peeked.contentType()?.charset() ?: Charsets.UTF_8
            val raw = peeked.bytes()
            val decoded = if (Snoop.config.decodeBodies) {
                decodeBody(raw, response.header("Content-Encoding"))
            } else {
                raw
            }
            String(decoded, charset)
        } catch (_: Exception) {
            null
        }

        Snoop.store.update(id) {
            it.copy(
                responseCode = response.code,
                responseMessage = response.message,
                responseHeaders = response.headers.map { h -> h.first to h.second },
                responseBody = responseBody,
                responseContentType = response.body?.contentType()?.toString(),
                responseBodySize = response.body?.contentLength() ?: -1L,
                status = TransactionStatus.COMPLETE,
                durationMs = elapsedMs(startNs),
            )
        }

        return response
    }

    private fun readRequestBody(request: Request): Pair<String?, Long> {
        val body = request.body ?: return null to 0L
        if (body.isDuplex() || body.isOneShot()) return null to safeContentLength(body)
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            val size = buffer.size
            val text = if (size > maxBodyBytes) {
                buffer.readUtf8(maxBodyBytes) + "\n… (truncated)"
            } else {
                buffer.readUtf8()
            }
            text to size
        } catch (_: Exception) {
            null to 0L
        }
    }

    private fun safeContentLength(body: okhttp3.RequestBody): Long =
        try {
            body.contentLength().coerceAtLeast(0L)
        } catch (_: IOException) {
            0L
        }

    private fun elapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000

    private fun registerReplay(id: Long, call: Call) {
        NetworkReplayRegistry.register(id) {
            call.clone().enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) = Unit
                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                    }
                },
            )
        }
    }

    private companion object {
        const val DEFAULT_MAX_BODY_BYTES = 250_000L
    }
}
