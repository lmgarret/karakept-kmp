package com.karakept.app.data.remote

import com.karakept.app.utils.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

const val CONNECT_TIMEOUT_MS = 10_000L
const val SOCKET_TIMEOUT_MS = 30_000L
const val REQUEST_TIMEOUT_MS = 60_000L
const val ASSET_REQUEST_TIMEOUT_MS = 300_000L
const val ASSET_SOCKET_TIMEOUT_MS = 120_000L

fun createHttpClient(): HttpClient {
    return HttpClient { applyKarakeptClientDefaults() }
}

/**
 * Shared client configuration, extracted so tests can apply the exact production
 * setup (timeouts, retry policy, serialization) onto a mock engine.
 */
internal fun HttpClientConfig<*>.applyKarakeptClientDefaults() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
            explicitNulls = false  // Don't send explicit null values (server rejects them)
            encodeDefaults = true  // Ensure fields with default values are serialized
        })
    }

    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) =
                message.lines().forEach { AppLogger.d("HttpClient", it) }
        }
        level = LogLevel.INFO
    }

    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = SOCKET_TIMEOUT_MS
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
    }

    // Only GETs are retried: mutations are replayed by the pending-action queue,
    // and a transport-level retry of a non-idempotent call (e.g. attaching tags)
    // could double-apply it.
    install(HttpRequestRetry) {
        maxRetries = 2
        exponentialDelay()
        retryIf { request, response ->
            request.method == HttpMethod.Get && response.status.value in 500..599
        }
        retryOnExceptionIf { request, cause ->
            request.method == HttpMethod.Get && cause !is CancellationException
        }
    }

    defaultRequest {
        contentType(ContentType.Application.Json)
    }
}
