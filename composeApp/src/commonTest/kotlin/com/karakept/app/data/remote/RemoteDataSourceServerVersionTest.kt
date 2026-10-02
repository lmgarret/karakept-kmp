package com.karakept.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
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
import kotlin.test.assertNull

class RemoteDataSourceServerVersionTest {

    private fun sourceFor(engine: MockEngine) =
        RemoteDataSource(HttpClient(engine) { applyKarakeptClientDefaults() })

    // Client calls run on a real dispatcher — under the test scheduler's virtual time the
    // HttpTimeout budget expires instantly while MockEngine responds elsewhere.
    private fun runRealTime(block: suspend CoroutineScope.() -> Unit) = runTest {
        withContext(Dispatchers.Default) { block() }
    }

    private fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine { _ ->
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    @Test
    fun readsVersionFromUnauthenticatedRoute() = runRealTime {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond("""{"version":"0.31.0"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val version = sourceFor(engine).fetchServerVersion("https://kk.example.com/")

        assertEquals("0.31.0", version)
        assertEquals("https://kk.example.com/api/version", captured?.url.toString())
        assertNull(captured?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun stripsApiV1SuffixFromServerUrl() = runRealTime {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond("""{"version":"nightly"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        assertEquals("nightly", sourceFor(engine).fetchServerVersion("https://kk.example.com/api/v1"))
        assertEquals("https://kk.example.com/api/version", captured?.url.toString())
    }

    @Test
    fun missingRouteMeansNoVersion() = runRealTime {
        assertNull(sourceFor(json("Not Found", HttpStatusCode.NotFound)).fetchServerVersion("https://kk.example.com"))
    }

    @Test
    fun serverErrorThrowsRatherThanReportingOutdated() = runRealTime {
        assertFailsWith<ApiException> {
            sourceFor(json("oops", HttpStatusCode.BadGateway)).fetchServerVersion("https://kk.example.com")
        }
    }

    @Test
    fun unexpectedBodyThrows() = runRealTime {
        assertFailsWith<ApiException> {
            sourceFor(json("<html></html>")).fetchServerVersion("https://kk.example.com")
        }
    }
}
