package com.karakept.app.data.remote

import com.karakept.api.client.*
import com.karakept.app.data.model.Server
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

/**
 * What every [RemoteDataSource] call is built from: the HTTP client, the offline-mode guard and
 * the per-server URL and auth plumbing. The calls themselves are extensions on this class, split
 * by concern across the `RemoteDataSource*.kt` files; [RemoteDataSource] is the facade over them.
 */
internal class RemoteApiContext(
    val client: HttpClient,
    private val offlineModeProvider: (suspend () -> Boolean)? = null
) {
    /**
     * Guard function that blocks execution when the user has enabled offline mode.
     * Throws OfflineModeException when offline.
     */
    suspend fun <T> guardedCall(block: suspend () -> T): T {
        if (offlineModeProvider?.invoke() == true) {
            throw OfflineModeException()
        }
        return block()
    }

    val trpcJson = Json { ignoreUnknownKeys = true }

    fun getBaseUrl(server: Server): String {
        val base = if (server.url.endsWith("/")) server.url.removeSuffix("/") else server.url
        return if (base.endsWith("/api/v1")) base else "$base/api/v1"
    }

    fun getTrpcBaseUrl(server: Server): String {
        val base = if (server.url.endsWith("/")) server.url.removeSuffix("/") else server.url
        return if (base.endsWith("/api/v1")) base.removeSuffix("/api/v1") else base
    }

    fun bookmarksApi(server: Server) = BookmarksApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }

    fun listsApi(server: Server) = ListsApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }

    fun tagsApi(server: Server) = TagsApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }

    fun highlightsApi(server: Server) = HighlightsApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }

    fun usersApi(server: Server) = UsersApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }

    fun getAuth(server: Server) = "Bearer ${server.apiKey}"
}
