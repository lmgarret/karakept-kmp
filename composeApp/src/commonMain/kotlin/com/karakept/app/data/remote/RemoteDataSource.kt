package com.karakept.app.data.remote

import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.model.BookmarkDto
import com.karakept.app.data.remote.model.PaginatedBookmarksResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess

class RemoteDataSource(private val client: HttpClient) {
    
    suspend fun fetchBookmarks(server: Server): List<BookmarkDto> {
        return try {
            val response: HttpResponse = client.get(server.url) {
                url {
                    appendPathSegments("api", "v1", "bookmarks")
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }
            
            if (!response.status.isSuccess()) {
                throw ApiException("Failed to fetch bookmarks: ${response.status}")
            }
            
            val paginatedResponse: PaginatedBookmarksResponse = response.body()
            paginatedResponse.bookmarks
        } catch (e: Exception) {
            throw ApiException("Error fetching bookmarks: ${e.message}", e)
        }
    }
}

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
