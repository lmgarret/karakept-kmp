package com.karakept.app.data.remote

import com.karakept.app.data.model.Server
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for the production HTTP client configuration ([applyKarakeptClientDefaults]):
 * GET-only transport retries and timeout wiring.
 *
 * Client calls run on a real dispatcher — under the test scheduler's virtual time
 * the HttpTimeout budget expires instantly while MockEngine responds elsewhere.
 */
class KtorClientConfigTest {

    private fun clientFor(engine: MockEngine) = HttpClient(engine) { applyKarakeptClientDefaults() }

    private fun runRealTime(block: suspend CoroutineScope.() -> Unit) = runTest {
        withContext(Dispatchers.Default) { block() }
    }

    @Test
    fun get_retriedOnServerError() = runRealTime {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            if (attempts < 3) {
                respondError(HttpStatusCode.ServiceUnavailable)
            } else {
                respond("ok", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/plain"))
            }
        }

        val response = clientFor(engine).get("https://example.com/api/v1/bookmarks")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(3, attempts, "GET should be retried up to 2 times on 5xx")
    }

    @Test
    fun get_notRetriedBeyondMaxRetries() = runRealTime {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respondError(HttpStatusCode.InternalServerError)
        }

        val response = clientFor(engine).get("https://example.com/api/v1/bookmarks")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(3, attempts, "GET should stop after initial attempt + 2 retries")
    }

    @Test
    fun get_notRetriedOnClientError() = runRealTime {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respondError(HttpStatusCode.Unauthorized)
        }

        val response = clientFor(engine).get("https://example.com/api/v1/bookmarks")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, attempts, "4xx responses must not be retried")
    }

    @Test
    fun post_neverRetried() = runRealTime {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respondError(HttpStatusCode.ServiceUnavailable)
        }

        val response = clientFor(engine).post("https://example.com/api/v1/bookmarks")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(1, attempts, "Mutations must not be transport-retried (queue replays them)")
    }

    @Test
    fun get_retriedOnTransportException() = runRealTime {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            throw kotlinx.io.IOException("connection reset")
        }

        assertFailsWith<kotlinx.io.IOException> {
            clientFor(engine).get("https://example.com/api/v1/bookmarks")
        }
        assertEquals(3, attempts, "GET should be retried on transport exceptions before giving up")
    }

    @Test
    fun timeoutSurfacesAsApiExceptionThroughGuardedCall() = runRealTime {
        // A request exceeding the budget should surface as ApiException from
        // RemoteDataSource, not hang forever (pre-change there was no HttpTimeout at all).
        val engine = MockEngine {
            throw io.ktor.client.plugins.HttpRequestTimeoutException("https://example.com", REQUEST_TIMEOUT_MS)
        }
        val remoteDataSource = RemoteDataSource(clientFor(engine))
        val server = Server(id = "s1", url = "https://example.com", apiKey = "k", label = "t")

        assertFailsWith<ApiException> {
            remoteDataSource.fetchBookmarks(server)
        }
    }
}
