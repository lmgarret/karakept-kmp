package com.karakept.app.data.remote

import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.model.CreateBookmarkDto
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
    
    suspend fun fetchBookmarks(
        server: Server,
        cursor: String? = null,
        limit: Int = 50,
        includeContent: Boolean = false,
        archived: Boolean? = null,
        favourited: Boolean? = null
    ): PaginatedBookmarksResponse {
        return try {
            val response: HttpResponse = client.get(server.url) {
                url {
                    appendPathSegments("api", "v1", "bookmarks")
                    if (cursor != null) parameters.append("cursor", cursor)
                    parameters.append("limit", limit.toString())
                    parameters.append("include_content", includeContent.toString())
                    if (archived != null) parameters.append("archived", archived.toString())
                    if (favourited != null) parameters.append("favourited", favourited.toString())
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }

            if (!response.status.isSuccess()) {
                throw ApiException("Failed to fetch bookmarks: ${response.status}")
            }

            response.body()
        } catch (e: Exception) {
            throw ApiException("Error fetching bookmarks: ${e.message}", e)
        }
    }

    suspend fun fetchBookmark(server: Server, bookmarkId: String, includeContent: Boolean = true): BookmarkDto {
        return try {
            val response: HttpResponse = client.get(server.url) {
                url {
                    appendPathSegments("api", "v1", "bookmarks", bookmarkId)
                    parameters.append("include_content", includeContent.toString())
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
            
            // Try to parse as ListsResponse first (standard)
            try {
                val listsResponse: ListsResponse = response.body()
                listsResponse.lists
            } catch (e: Exception) {
                // Fallback: Try to parse as direct List<ListDto>
                try {
                    val directList: List<ListDto> = response.body()
                    directList
                } catch (e2: Exception) {
                    throw e // Throw original error if both fail
                }
            }
        } catch (e: Exception) {
            throw ApiException("Error fetching lists: ${e.message}", e)
        }
    }

    suspend fun fetchBookmarksForList(server: Server, listId: String, includeContent: Boolean = false): List<BookmarkDto> {
        return try {
            val response: HttpResponse = client.get(server.url) {
                url {
                    // Endpoint requires /api/v1 prefix like other endpoints
                    appendPathSegments("api", "v1", "lists", listId, "bookmarks")
                    parameters.append("include_content", includeContent.toString())
                }
                header("Authorization", "Bearer ${server.apiKey}")
            }

            if (!response.status.isSuccess()) {
                println("Failed to fetch bookmarks for list $listId: ${response.status}")
                throw ApiException("Failed to fetch bookmarks for list $listId: ${response.status}")
            }

            // Try multiple response formats to handle API variations
            try {
                val paginatedResponse: PaginatedBookmarksResponse = response.body()
                println("List $listId: Parsed as PaginatedBookmarksResponse - ${paginatedResponse.bookmarks.size} bookmarks")
                paginatedResponse.bookmarks
            } catch (e: Exception) {
                // If PaginatedBookmarksResponse fails, try parsing as direct list
                try {
                    val directList: List<BookmarkDto> = response.body()
                    println("List $listId: Parsed as List<BookmarkDto> - ${directList.size} bookmarks")
                    directList
                } catch (e2: Exception) {
                    // Log both parsing errors for debugging
                    println("List $listId: Failed to parse as PaginatedBookmarksResponse: ${e.message}")
                    println("List $listId: Failed to parse as List<BookmarkDto>: ${e2.message}")
                    throw e // Throw original error
                }
            }
        } catch (e: Exception) {
            // Log the error instead of silently swallowing it
            println("Error fetching bookmarks for list $listId: ${e.message}")
            e.printStackTrace()
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
     * Create a new bookmark
     * POST /api/v1/bookmarks
     */
    suspend fun createBookmark(server: Server, url: String): BookmarkDto {
        return try {
            val response: HttpResponse = client.post(server.url) {
                url {
                    appendPathSegments("api", "v1", "bookmarks")
                }
                header("Authorization", "Bearer ${server.apiKey}")
                setBody(CreateBookmarkDto(type = "link", url = url))
            }

            if (!response.status.isSuccess()) {
                throw ApiException("Failed to create bookmark: ${response.status}")
            }

            response.body()
        } catch (e: Exception) {
            throw ApiException("Error creating bookmark: ${e.message}", e)
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

