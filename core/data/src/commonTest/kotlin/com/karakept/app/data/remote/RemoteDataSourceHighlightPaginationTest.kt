package com.karakept.app.data.remote

import com.karakept.app.data.model.Server
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Tests for [RemoteDataSource.fetchAllHighlights] cursor pagination.
 *
 * Regression: highlights used to be fetched with a single limit=100 request,
 * silently truncating libraries with more than 100 highlights (and causing
 * local deletion of everything beyond the first page during sync).
 */
class RemoteDataSourceHighlightPaginationTest {

    private val testServer = Server(
        id = "server1",
        url = "https://example.com",
        apiKey = "test-key",
        label = "Test Server"
    )

    private fun highlightJson(id: String) = """
        {"bookmarkId":"bk-1","startOffset":0,"endOffset":10,"color":"yellow",
         "text":"text-$id","note":null,"id":"$id","userId":"u1","createdAt":"2026-01-01T00:00:00Z"}
    """.trimIndent()

    private fun pageJson(ids: List<String>, nextCursor: String?) = buildString {
        append("""{"highlights":[""")
        append(ids.joinToString(",") { highlightJson(it) })
        append("""],"nextCursor":${if (nextCursor != null) "\"$nextCursor\"" else "null"}}""")
    }

    private fun clientFor(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }
    }

    @Test
    fun fetchAllHighlights_followsCursorAcrossPages() = runTest {
        val requestCursors = mutableListOf<String?>()
        val engine = MockEngine { request ->
            val cursor = request.url.parameters["cursor"]
            requestCursors.add(cursor)
            val body = when (cursor) {
                null -> pageJson((1..100).map { "h$it" }, nextCursor = "cursor-2")
                "cursor-2" -> pageJson((101..130).map { "h$it" }, nextCursor = null)
                else -> error("Unexpected cursor $cursor")
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val remoteDataSource = RemoteDataSource(clientFor(engine))

        val highlights = remoteDataSource.fetchAllHighlights(testServer)

        assertEquals(130, highlights.size, "All pages should be fetched")
        assertEquals(listOf(null, "cursor-2"), requestCursors, "Second request should carry the cursor")
        assertEquals("h1", highlights.first().id)
        assertEquals("h130", highlights.last().id)
    }

    @Test
    fun fetchAllHighlights_singlePageWithoutCursorCompletes() = runTest {
        val engine = MockEngine {
            respond(
                content = pageJson(listOf("h1", "h2"), nextCursor = null),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val remoteDataSource = RemoteDataSource(clientFor(engine))

        val highlights = remoteDataSource.fetchAllHighlights(testServer)

        assertEquals(2, highlights.size)
        assertNull(engine.requestHistory.single().url.parameters["cursor"])
    }

    @Test
    fun fetchAllHighlights_midPaginationFailureThrows() = runTest {
        val engine = MockEngine { request ->
            when (request.url.parameters["cursor"]) {
                null -> respond(
                    content = pageJson(listOf("h1"), nextCursor = "cursor-2"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond(content = "boom", status = HttpStatusCode.InternalServerError)
            }
        }
        val remoteDataSource = RemoteDataSource(clientFor(engine))

        assertFailsWith<ApiException> {
            remoteDataSource.fetchAllHighlights(testServer)
        }
    }
}
