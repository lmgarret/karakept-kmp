package com.karakept.app.data.remote

import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.model.BookmarkDto
import com.karakept.app.data.remote.model.ListDto
import com.karakept.app.data.remote.model.ListsResponse
import com.karakept.app.data.remote.model.PaginatedBookmarksResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.setBody
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

    suspend fun fetchBookmark(server: Server, bookmarkId: String): BookmarkDto {
        return try {
            val response: HttpResponse = client.get(server.url) {
                url {
                    appendPathSegments("api", "v1", "bookmarks", bookmarkId)
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }

            if (!response.status.isSuccess()) {
                throw ApiException("Failed to fetch bookmark $bookmarkId: ${response.status}")
            }

            response.body()
        } catch (e: Exception) {
            throw ApiException("Error fetching bookmark: ${e.message}", e)
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

    suspend fun fetchBookmarksForList(server: Server, listId: String): List<BookmarkDto> {
        return try {
            val response: HttpResponse = client.get(server.url) {
                url {
                    appendPathSegments("api", "v1", "lists", listId, "bookmarks")
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }
            
            if (!response.status.isSuccess()) {
                throw ApiException("Failed to fetch bookmarks for list $listId: ${response.status}")
            }
            
            val paginatedResponse: PaginatedBookmarksResponse = response.body()
            paginatedResponse.bookmarks
        } catch (e: Exception) {
            // If a list fetch fails, we return empty list to not break the whole sync
            emptyList()
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
    suspend fun downloadAsset(server: Server, assetId: String): ByteArray {
        return try {
            val response: HttpResponse = client.get(server.url) {
                url {
                    appendPathSegments("api", "v1", "assets", assetId)
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }
            
            if (!response.status.isSuccess()) {
                throw ApiException("Failed to download asset $assetId: ${response.status}")
            }
            
            response.body()
        } catch (e: Exception) {
            throw ApiException("Error downloading asset: ${e.message}", e)
        }
    }

    /**
     * Update a bookmark (archive, favourite, etc.)
     * PATCH /api/v1/bookmarks/:bookmarkId
     */
    suspend fun updateBookmark(
        server: Server,
        bookmarkId: String,
        updates: com.karakept.app.data.remote.model.UpdateBookmarkDto
    ): BookmarkDto {
        return try {
            val response: HttpResponse = client.patch(server.url) {
                url {
                    appendPathSegments("api", "v1", "bookmarks", bookmarkId)
                }
                header("Authorization", "Bearer ${server.apiKey}")
                setBody(updates)
            }
            
            if (!response.status.isSuccess()) {
                throw ApiException("Failed to update bookmark $bookmarkId: ${response.status}")
            }
            
            response.body()
        } catch (e: Exception) {
            throw ApiException("Error updating bookmark: ${e.message}", e)
        }
    }

    /**
     * Delete a bookmark
     * DELETE /api/v1/bookmarks/:bookmarkId
     */
    suspend fun deleteBookmark(server: Server, bookmarkId: String) {
        try {
            val response: HttpResponse = client.delete(server.url) {
                url {
                    appendPathSegments("api", "v1", "bookmarks", bookmarkId)
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }
            
            if (!response.status.isSuccess()) {
                throw ApiException("Failed to delete bookmark $bookmarkId: ${response.status}")
            }
        } catch (e: Exception) {
            throw ApiException("Error deleting bookmark: ${e.message}", e)
        }
    }

    /**
     * Attach tags to a bookmark
     * POST /api/v1/bookmarks/:bookmarkId/tags
     */
    suspend fun attachTags(
        server: Server,
        bookmarkId: String,
        tags: List<String>
    ): com.karakept.app.data.remote.model.AttachTagsResponse {
        return try {
            val response: HttpResponse = client.post(server.url) {
                url {
                    appendPathSegments("api", "v1", "bookmarks", bookmarkId, "tags")
                }
                header("Authorization", "Bearer ${server.apiKey}")
                setBody(com.karakept.app.data.remote.model.AttachTagsDto(tags))
            }
            
            if (!response.status.isSuccess()) {
                throw ApiException("Failed to attach tags to bookmark $bookmarkId: ${response.status}")
            }
            
            response.body()
        } catch (e: Exception) {
            throw ApiException("Error attaching tags: ${e.message}", e)
        }
    }

    /**
     * Detach a tag from a bookmark
     * DELETE /api/v1/bookmarks/:bookmarkId/tags/:tagId
     */
    suspend fun detachTag(server: Server, bookmarkId: String, tagId: String) {
        try {
            val response: HttpResponse = client.delete(server.url) {
                url {
                    appendPathSegments("api", "v1", "bookmarks", bookmarkId, "tags", tagId)
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }
            
            if (!response.status.isSuccess()) {
                throw ApiException("Failed to detach tag from bookmark $bookmarkId: ${response.status}")
            }
        } catch (e: Exception) {
            throw ApiException("Error detaching tag: ${e.message}", e)
        }
    }

    /**
     * Add a bookmark to a list
     * POST /api/v1/lists/:listId/bookmarks/:bookmarkId
     */
    suspend fun addBookmarkToList(server: Server, listId: String, bookmarkId: String) {
        try {
            val response: HttpResponse = client.post(server.url) {
                url {
                    appendPathSegments("api", "v1", "lists", listId, "bookmarks", bookmarkId)
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }
            
            if (!response.status.isSuccess()) {
                throw ApiException("Failed to add bookmark $bookmarkId to list $listId: ${response.status}")
            }
        } catch (e: Exception) {
            throw ApiException("Error adding bookmark to list: ${e.message}", e)
        }
    }

    /**
     * Remove a bookmark from a list
     * DELETE /api/v1/lists/:listId/bookmarks/:bookmarkId
     */
    suspend fun removeBookmarkFromList(server: Server, listId: String, bookmarkId: String) {
        try {
            val response: HttpResponse = client.delete(server.url) {
                url {
                    appendPathSegments("api", "v1", "lists", listId, "bookmarks", bookmarkId)
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }
            
            if (!response.status.isSuccess()) {
                throw ApiException("Failed to remove bookmark $bookmarkId from list $listId: ${response.status}")
            }
        } catch (e: Exception) {
            throw ApiException("Error removing bookmark from list: ${e.message}", e)
        }
    }
}

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

