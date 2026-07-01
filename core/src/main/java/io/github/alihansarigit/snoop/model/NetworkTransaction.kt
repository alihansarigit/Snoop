package io.github.alihansarigit.snoop.model

/** Lifecycle state of a captured HTTP exchange. */
enum class TransactionStatus { PENDING, COMPLETE, FAILED }

/**
 * A single captured HTTP request/response pair. Transport agnostic: adapters
 * (OkHttp, Ktor, …) translate their own types into this model.
 */
data class NetworkTransaction(
    val id: Long,
    val method: String,
    val url: String,
    val host: String = "",
    val path: String = "",
    val requestHeaders: List<Pair<String, String>> = emptyList(),
    val requestBody: String? = null,
    val requestContentType: String? = null,
    val requestBodySize: Long = 0,
    val responseCode: Int? = null,
    val responseMessage: String? = null,
    val responseHeaders: List<Pair<String, String>> = emptyList(),
    val responseBody: String? = null,
    val responseContentType: String? = null,
    val responseBodySize: Long = 0,
    val status: TransactionStatus = TransactionStatus.PENDING,
    val error: String? = null,
    val requestDate: Long = 0,
    val durationMs: Long? = null,
) {
    val isError: Boolean
        get() = status == TransactionStatus.FAILED || (responseCode != null && responseCode >= 400)
}
