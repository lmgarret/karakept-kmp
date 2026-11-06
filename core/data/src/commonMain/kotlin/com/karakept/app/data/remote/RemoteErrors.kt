package com.karakept.app.data.remote

import io.ktor.client.statement.bodyAsText
import com.karakept.api.infrastructure.HttpResponse as ApiHttpResponse

/**
 * Exception thrown when a network request is blocked due to offline mode being enabled.
 */
class OfflineModeException(message: String = "Offline mode is enabled - network request blocked") : Exception(message)

/**
 * Thrown when the server does not expose a tRPC route Karakept relies on. tRPC is Karakeep's
 * internal API, so routes can disappear or be renamed between versions — callers should report
 * this as "your server doesn't support this" rather than as a transient failure worth retrying.
 */
class UnsupportedServerActionException(message: String) : Exception(message)

class ApiException(
    message: String,
    cause: Throwable? = null,
    statusCode: Int? = null
) : Exception(message, cause) {
    /** HTTP status of the failed response, preserved through re-wraps via [cause]. */
    val statusCode: Int? = statusCode ?: (cause as? ApiException)?.statusCode
}

/** True when this failure chain contains an HTTP response with [code]. */
fun Throwable.hasHttpStatus(code: Int): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is ApiException && current.statusCode == code) return true
        current = current.cause
    }
    return false
}

/** "URI too long" and "request header fields too large" — the proxy, not the server. */
internal val URL_TOO_LONG_STATUSES = setOf(414, 431)

/**
 * Extension that checks the HTTP status of a generated API response before deserializing.
 * If the response is non-2xx, throws [ApiException] with the status code and response body,
 * preventing cryptic [io.ktor.client.call.NoTransformationFoundException] errors when the
 * server returns a non-JSON error response (e.g. 401 text/plain).
 */
internal suspend fun <T : Any> ApiHttpResponse<T>.checkedBody(): T {
    if (!success) {
        val errorBody = try { response.bodyAsText() } catch (_: Exception) { "(unreadable)" }
        throw ApiException("HTTP $status: $errorBody", statusCode = status)
    }
    return body()
}
