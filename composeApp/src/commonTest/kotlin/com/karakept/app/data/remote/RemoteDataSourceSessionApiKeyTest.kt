package com.karakept.app.data.remote

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Wire-level tests for trading a web session for an API key (SSO/OIDC sign-in). */
class RemoteDataSourceSessionApiKeyTest {

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
    fun postsToApiKeysCreateWithTheSessionCookieAndReturnsTheKey() = runRealTime {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                """[{"result":{"data":{"json":{"id":"k1","name":"Karakept","key":"ak1_abc_def",""" +
                    """"createdAt":"2026-09-25T00:00:00.000Z"},"meta":{"values":{"createdAt":["Date"]}}}}}]""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val key = sourceFor(engine).createApiKeyFromSession(
            serverUrl = "https://kk.example.com/api/v1",
            cookieHeader = "__Secure-next-auth.session-token=jwt"
        )

        assertEquals("ak1_abc_def", key)
        val request = captured!!
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://kk.example.com/api/trpc/apiKeys.create?batch=1", request.url.toString())
        assertEquals("__Secure-next-auth.session-token=jwt", request.headers[HttpHeaders.Cookie])
        assertNull(request.headers[HttpHeaders.Authorization])
        assertEquals("""{"0":{"json":{"name":"Karakept"}}}""", bodyText(request))
    }

    @Test
    fun unauthenticatedSessionSurfacesA401() = runRealTime {
        val engine = MockEngine {
            respond(
                """[{"error":{"json":{"message":"UNAUTHORIZED","code":-32001}}}]""",
                HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val error = assertFailsWith<ApiException> {
            sourceFor(engine).createApiKeyFromSession("https://kk.example.com", "x=y")
        }
        assertTrue(error.hasHttpStatus(401))
    }

    @Test
    fun responseWithoutAKeyFails() = runRealTime {
        val engine = MockEngine {
            respond(
                """[{"result":{"data":{"json":{}}}}]""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        assertFailsWith<ApiException> {
            sourceFor(engine).createApiKeyFromSession("https://kk.example.com", "x=y")
        }
    }
}
