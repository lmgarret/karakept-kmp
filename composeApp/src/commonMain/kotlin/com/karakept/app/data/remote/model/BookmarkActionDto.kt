package com.karakept.app.data.remote.model

import kotlinx.serialization.Serializable

/**
 * DTO for updating a bookmark via PATCH /api/v1/bookmarks/:id
 */
@Serializable
data class UpdateBookmarkDto(
    val title: String? = null,
    val archived: Boolean? = null,
    val favourited: Boolean? = null,
    val note: String? = null
)

/**
 * DTO for managing tags on a bookmark
 */
@Serializable
data class AttachTagsDto(
    val tags: List<String>
)

/**
 * Response when attaching tags
 */
@Serializable
data class AttachTagsResponse(
    val attached: List<BookmarkTag>
)

/**
 * DTO for detaching a tag
 */
@Serializable
data class DetachTagDto(
    val tagId: String
)
