package com.karakept.app.data.remote

import com.karakept.app.data.model.Server
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import kotlin.test.assertTrue

/**
 * Wire-level tests for the three AI routes.
 *
 * Two of them have no generated client to keep them honest — re-tagging and the admin probe are
 * tRPC — and all three have to tell "this server won't do it" apart from "the request failed",
 * because only the first is worth hiding the action for.
 */
class RemoteDataSourceAiActionsTest {

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

    // ── summarize ──────────────────────────────────────────────

    @Test
    fun summarize_postsToRestRouteAndReturnsSummary() = runRealTime {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                """{"id":"bm-1","createdAt":"2024-01-01","modifiedAt":null,"archived":false,""" +
                    """"favourited":false,"taggingStatus":"success","summarizationStatus":"success",""" +
                    """"userId":"u1","summary":"A short summary."}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val result = sourceFor(engine).summarizeBookmark(server, "bm-1")

        assertEquals("A short summary.", result.summary)
        assertEquals("success", result.summarizationStatus)
        assertEquals(HttpMethod.Post, captured?.method)
        assertEquals(
            "https://kk.example.com/api/v1/bookmarks/bm-1/summarize",
            captured?.url.toString()
        )
    }

    @Test
    fun summarize_reportsUnsupportedWhenServerHasNoInferenceClient() = runRealTime {
        val engine = MockEngine {
            respond(
                """{"code":"BAD_REQUEST","message":"No inference client configured"}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        // Not an ApiException: the caller uses this to stop offering the action on this server.
        assertFailsWith<UnsupportedServerActionException> {
            sourceFor(engine).summarizeBookmark(server, "bm-1")
        }
    }

    @Test
    fun summarize_surfacesOtherFailuresAsApiException() = runRealTime {
        val engine = MockEngine {
            respond("upstream exploded", HttpStatusCode.InternalServerError)
        }

        assertFailsWith<ApiException> { sourceFor(engine).summarizeBookmark(server, "bm-1") }
    }

    // ── re-tag ─────────────────────────────────────────────────

    @Test
    fun retag_postsAdminTrpcBatchEnvelope() = runRealTime {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                """[{"result":{"data":{"json":null}}}]""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        sourceFor(engine).requestAiRetag(server, "bm-1")

        assertEquals(HttpMethod.Post, captured?.method)
        assertEquals(
            "https://kk.example.com/api/trpc/admin.adminRetagBookmark?batch=1",
            captured?.url.toString()
        )
        assertEquals("""{"0":{"json":{"bookmarkId":"bm-1"}}}""", bodyText(captured!!))
    }

    @Test
    fun retag_reportsUnsupportedWhenRouteIsMissing() = runRealTime {
        val engine = MockEngine {
            respond(
                """{"error":{"message":"No procedure found on path admin.adminRetagBookmark"}}""",
                HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        assertFailsWith<UnsupportedServerActionException> {
            sourceFor(engine).requestAiRetag(server, "bm-1")
        }
    }

    @Test
    fun retag_reportsUnsupportedForNonAdminKey() = runRealTime {
        val engine = MockEngine {
            respond(
                """{"error":{"message":"UNAUTHORIZED"}}""",
                HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val failure = assertFailsWith<UnsupportedServerActionException> {
            sourceFor(engine).requestAiRetag(server, "bm-1")
        }
        assertTrue(failure.message!!.contains("admin"))
    }

    @Test
    fun retag_surfacesOtherFailuresAsApiException() = runRealTime {
        val engine = MockEngine { respond("boom", HttpStatusCode.InternalServerError) }

        assertFailsWith<ApiException> { sourceFor(engine).requestAiRetag(server, "bm-1") }
    }

    // ── admin probe ────────────────────────────────────────────

    @Test
    fun adminProbe_queriesTheAdminOnlyRouteAndReportsTrueOnSuccess() = runRealTime {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                """[{"result":{"data":{"json":{}}}}]""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        assertTrue(sourceFor(engine).isServerAdmin(server))
        assertEquals(HttpMethod.Get, captured?.method)
        assertEquals(
            "https://kk.example.com/api/trpc/admin.getAdminNoticies?batch=1",
            captured?.url.toString()
        )
    }

    @Test
    fun adminProbe_reportsFalseForANonAdminKey() = runRealTime {
        val engine = MockEngine {
            respond(
                """{"error":{"message":"UNAUTHORIZED"}}""",
                HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        assertFalse(sourceFor(engine).isServerAdmin(server))
    }

    @Test
    fun adminProbe_throwsOnTransportFailureRatherThanClaimingNotAdmin() = runRealTime {
        val engine = MockEngine { throw kotlinx.io.IOException("connection reset") }

        // Caching "not an admin" because the network blipped would hide the action until restart.
        assertFailsWith<ApiException> { sourceFor(engine).isServerAdmin(server) }
    }
}
