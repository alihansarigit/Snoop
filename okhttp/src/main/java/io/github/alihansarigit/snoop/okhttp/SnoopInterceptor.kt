package io.github.alihansarigit.snoop.okhttp

import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.model.NetworkTransaction
import io.github.alihansarigit.snoop.model.TransactionStatus
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
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

        Snoop.store.put(
            NetworkTransaction(
                id = id,
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
            response.peekBody(maxBodyBytes).string()
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

    private companion object {
        const val DEFAULT_MAX_BODY_BYTES = 250_000L
    }
}
