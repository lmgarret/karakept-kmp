package com.karakept.app.data.remote

import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.model.BookmarkDto
import com.karakept.app.data.remote.model.ListDto
import com.karakept.app.data.remote.model.ListsResponse
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

    suspend fun fetchLists(server: Server): List<ListDto> {
        return try {
            val response: HttpResponse = client.get(server.url) {
                url {
                    appendPathSegments("api", "v1", "lists")
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }
            
            if (!response.status.isSuccess()) {
                throw ApiException("Failed to fetch lists: ${response.status}")
            }
            
            val listsResponse: ListsResponse = response.body()
            listsResponse.lists
        } catch (e: Exception) {
            throw ApiException("Error fetching lists: ${e.message}", e)
        }
    }

    suspend fun testConnection(url: String, apiKey: String): Boolean {
        return try {
            val response: HttpResponse = client.get(url) {
                url {
                    appendPathSegments("api", "v1", "bookmarks")
                    parameters.append("page", "1")
                    parameters.append("per_page", "1")
                }
                header("Authorization", "Bearer $apiKey")
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }
}

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
