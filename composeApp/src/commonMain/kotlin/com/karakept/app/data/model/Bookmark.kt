package com.karakept.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Bookmark(
    val id: Long,
    val serverId: String, // Foreign key to Server
    val url: String,
    val title: String,
    val content: String?, // HTML content
    val createdAt: Long,
    val isArchived: Boolean = false,
    val isStarred: Boolean = false
)
