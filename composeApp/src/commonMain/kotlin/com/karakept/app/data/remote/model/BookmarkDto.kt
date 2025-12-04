package com.karakept.app.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class BookmarkDto(
    val id: String,
    val createdAt: String,
    val modifiedAt: String? = null,
    val title: String? = null,
    val archived: Boolean = false,
    val favourited: Boolean = false,
    val taggingStatus: String? = null,
    val summarizationStatus: String? = null,
    val note: String? = null,
    val summary: String? = null,
    val source: String? = null,
    val userId: String? = null,
    val tags: List<BookmarkTag> = emptyList(),
    val content: BookmarkContent,
    val assets: List<AssetDto> = emptyList()
)

@Serializable
data class BookmarkTag(
    val id: String,
    val name: String,
    val attachedBy: String
)

@Serializable
data class BookmarkContent(
    val type: String,
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val text: String? = null,
    val htmlContent: String? = null
)
