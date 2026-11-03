package com.karakept.app.data.remote

import com.karakept.app.data.model.Server
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wire-level tests for the two tRPC reading-progress routes. Karakeep exposes them only over
 * tRPC, so the request envelope has no generated client to keep it honest — and a push that
 * the server rejects must surface as a failure rather than be mistaken for a stored value.
 */
class RemoteDataSourceReadingProgressTest {

    private val server = Server(id = "s1", url = "https://kk.example.com", apiKey = "KEY", label = "t")

    private fun sourceFor(engine: MockEngine) =
        RemoteDataSource(HttpClient(engine) { applyKarakeptClientDefaults() })

    private fun bodyText(request: HttpRequestData): String =
        (request.body as? TextContent)?.text ?: request.body.toString()

    // Client calls run on a real dispatcher — under the test scheduler's virtual time the
    // HttpTimeout budget expires instantly while MockEngine responds elsewhere.
    private fun runRealTime(block: suspend CoroutineScope.() -> Unit) = runTest {
        withContext(Dispatchers.Default) { block() }
    }

    @Test
    fun push_sendsTrpcBatchEnvelopeAndReportsStored() = runRealTime {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                """[{"result":{"data":{"json":null}}}]""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val stored = sourceFor(engine).updateReadingProgress(server, "bm-123", 42)

        assertTrue(stored)
        val request = captured!!
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(
            "https://kk.example.com/api/trpc/bookmarks.updateReadingProgress?batch=1",
            request.url.toString()
        )
        assertEquals("Bearer KEY", request.headers[HttpHeaders.Authorization])
        assertEquals(
            """{"0":{"json":{"bookmarkId":"bm-123","readingProgressOffset":0,"readingProgressAnchor":null,"readingProgressPercent":42}}}""",
            bodyText(request)
        )
    }

    @Test
    fun push_nonLinkBookmarkRejection_isNotAnError() = runRealTime {
        val engine = MockEngine {
            respond(
                """{"error":{"json":{"message":"Reading progress can only be saved for link bookmarks"}}}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        assertFalse(sourceFor(engine).updateReadingProgress(server, "bm-123", 42))
    }

    @Test
    fun push_serverError_throwsWithStatus() = runRealTime {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError, "boom") }

        val e = assertFailsWith<ApiException> {
            sourceFor(engine).updateReadingProgress(server, "bm-123", 42)
        }
        assertEquals(500, e.statusCode)
    }

    @Test
    fun push_missingRoute_throwsWithStatus() = runRealTime {
        // A server too old to know the route answers 404. Reporting that as "not stored"
        // silently discarded the queued progress.
        val engine = MockEngine { respondError(HttpStatusCode.NotFound, "No procedure found") }

        val e = assertFailsWith<ApiException> {
            sourceFor(engine).updateReadingProgress(server, "bm-123", 42)
        }
        assertEquals(404, e.statusCode)
    }

    @Test
    fun push_staleApiKey_throwsWithStatus() = runRealTime {
        val engine = MockEngine { respondError(HttpStatusCode.Unauthorized, "UNAUTHORIZED") }

        val e = assertFailsWith<ApiException> {
            sourceFor(engine).updateReadingProgress(server, "bm-123", 42)
        }
        assertEquals(401, e.statusCode)
    }

    @Test
    fun pull_readsPercentFromTrpcBatchResponse() = runRealTime {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                """[{"result":{"data":{"json":{"readingProgressOffset":0,"readingProgressAnchor":null,"readingProgressPercent":68}}}}]""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        assertEquals(68, sourceFor(engine).getReadingProgressBatch(server, listOf("bm-123"))["bm-123"])
        val url = captured!!.url.toString()
        assertTrue(url.startsWith("https://kk.example.com/api/trpc/bookmarks.getReadingProgress?batch=1&input="))
    }

    @Test
    fun pull_noStoredProgress_returnsNull() = runRealTime {
        val engine = MockEngine {
            respond(
                """[{"result":{"data":{"json":{"readingProgressOffset":null,"readingProgressAnchor":null,"readingProgressPercent":null}}}}]""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        // Answered, holding nothing: present in the map with a null value.
        val answers = sourceFor(engine).getReadingProgressBatch(server, listOf("bm-123"))
        assertTrue(answers.containsKey("bm-123"))
        assertNull(answers["bm-123"])
    }

    @Test
    fun pull_serverError_leavesTheIdUnanswered() = runRealTime {
        // "We never found out" has to stay distinguishable from "nothing stored", and a chunk
        // that failed must not cost the answers the rest of the pass already paid for — so the
        // id is simply absent rather than present-and-null, and the other chunks survive.
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError, "boom") }

        val answers = sourceFor(engine).getReadingProgressBatch(server, listOf("bm-123"))

        assertTrue(answers.isEmpty())
    }
}
