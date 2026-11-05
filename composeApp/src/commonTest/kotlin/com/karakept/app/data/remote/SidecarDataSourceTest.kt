package com.karakept.app.data.remote

import com.karakept.app.data.model.SanitizeJobStatus
import com.karakept.app.data.model.SanitizeOutcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
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
import kotlin.test.assertTrue

/**
 * Tests for [SidecarDataSource] against a mock engine.
 *
 * The contract under test is the sidecar's own: 202 and 200 both hand back a job to poll, 404 means
 * "never asked about this bookmark", and 503 is the one refusal worth telling the user about.
 */
class SidecarDataSourceTest {

    private fun sourceFor(engine: MockEngine) =
        SidecarDataSource(HttpClient(engine) { applyKarakeptClientDefaults() })

    // Client calls run on a real dispatcher — under the test scheduler's virtual time the
    // HttpTimeout budget expires instantly while MockEngine responds elsewhere.
    private fun runRealTime(block: suspend CoroutineScope.() -> Unit) = runTest {
        withContext(Dispatchers.Default) { block() }
    }

    private fun jsonEngine(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }

    @Test
    fun capabilities_areParsed() = runRealTime {
        val source = sourceFor(
            jsonEngine(
                """{"service":"karhtmlscrub","llmEnabled":true,"maxContentSize":"400KB",""" +
                    """"assetName":"karhtmlscrub-sanitized.html","dryRun":false}"""
            )
        )

        val caps = source.fetchCapabilities("http://scrub:8080", null)

        assertEquals("karhtmlscrub", caps.service)
        assertTrue(caps.llmEnabled)
        assertTrue(caps.usable)
        assertEquals("400KB", caps.maxContentSize)
    }

    @Test
    fun apiPath_isAppendedOnce() = runRealTime {
        val seen = mutableListOf<String>()
        val engine = MockEngine { request: HttpRequestData ->
            seen += request.url.toString()
            respond(
                """{"llmEnabled":true}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val source = sourceFor(engine)

        source.fetchCapabilities("http://scrub:8080/", null)
        source.fetchCapabilities("http://scrub:8080/api/v1", null)

        // A user who pastes the full API URL must not end up at /api/v1/api/v1.
        assertTrue(
            seen.all { it == "http://scrub:8080/api/v1/capabilities" },
            seen.toString()
        )
    }

    @Test
    fun bearerToken_isSentWhenConfigured() = runRealTime {
        var auth: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            auth = request.headers[HttpHeaders.Authorization]
            respond(
                """{"llmEnabled":true}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        sourceFor(engine).fetchCapabilities("http://scrub:8080", "s3cret")

        assertEquals("Bearer s3cret", auth)
    }

    @Test
    fun sanitize_202_returnsTheNewJob() = runRealTime {
        val source = sourceFor(
            jsonEngine(
                """{"jobId":"j1","bookmarkId":"b1","status":"queued"}""",
                HttpStatusCode.Accepted
            )
        )

        val job = source.requestSanitize("http://scrub:8080", null, "b1")

        assertEquals("j1", job.jobId)
        assertEquals(SanitizeJobStatus.QUEUED, job.status)
        assertNull(job.outcome)
    }

    @Test
    fun sanitize_200_returnsTheJobAlreadyInFlight() = runRealTime {
        val source = sourceFor(
            jsonEngine("""{"jobId":"j1","bookmarkId":"b1","status":"running"}""")
        )

        assertEquals(
            SanitizeJobStatus.RUNNING,
            source.requestSanitize("http://scrub:8080", null, "b1").status
        )
    }

    @Test
    fun sanitize_503_saysTheServiceCannotClean() = runRealTime {
        val source = sourceFor(
            jsonEngine(
                """{"error":"no LLM configured; this deployment cannot sanitize"}""",
                HttpStatusCode.ServiceUnavailable
            )
        )

        assertFailsWith<SidecarUnavailableException> {
            source.requestSanitize("http://scrub:8080", null, "b1")
        }
    }

    @Test
    fun status_404_isNotAnError() = runRealTime {
        val source = sourceFor(
            jsonEngine("""{"error":"no job for this bookmark"}""", HttpStatusCode.NotFound)
        )

        // Jobs live in memory and are lost on restart, so "unknown" is a normal answer.
        assertNull(source.fetchSanitizeStatus("http://scrub:8080", null, "b1"))
    }

    @Test
    fun everyOutcome_theServiceReports_isUnderstood() = runRealTime {
        val wire = listOf(
            "uploaded" to SanitizeOutcome.UPLOADED,
            "skipped_filtered" to SanitizeOutcome.FILTERED,
            "skipped_no_content" to SanitizeOutcome.NO_CONTENT,
            "skipped_llm_disabled" to SanitizeOutcome.LLM_DISABLED,
            "skipped_too_large" to SanitizeOutcome.TOO_LARGE,
            "failed" to SanitizeOutcome.FAILED
        )
        wire.forEach { (value, expected) ->
            val source = sourceFor(
                jsonEngine("""{"jobId":"j1","bookmarkId":"b1","status":"done","outcome":"$value"}""")
            )

            val job = source.fetchSanitizeStatus("http://scrub:8080", null, "b1")

            assertEquals(expected, job?.outcome, "outcome $value")
            assertEquals(SanitizeJobStatus.DONE, job?.status)
        }
    }

    @Test
    fun unknownStatus_countsAsStillRunning() = runRealTime {
        val source = sourceFor(
            jsonEngine("""{"jobId":"j1","bookmarkId":"b1","status":"something-new"}""")
        )

        // Reporting a result the service never gave would be worse than polling once more.
        assertEquals(
            SanitizeJobStatus.RUNNING,
            source.fetchSanitizeStatus("http://scrub:8080", null, "b1")?.status
        )
    }

    @Test
    fun unauthorizedProbe_preservesTheStatus() = runRealTime {
        val source = sourceFor(MockEngine { respondError(HttpStatusCode.Unauthorized) })

        val error = assertFailsWith<ApiException> {
            source.fetchCapabilities("http://scrub:8080", "wrong")
        }
        assertEquals(401, error.statusCode)
    }
}
