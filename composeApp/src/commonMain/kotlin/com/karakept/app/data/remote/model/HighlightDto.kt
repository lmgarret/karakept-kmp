package com.karakept.app.data.remote.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class HighlightDto(
    val id: String,
    val bookmarkId: String,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val note: String? = null,
    val color: String? = null,
    val createdAt: String,
    val userId: String? = null
)

@Serializable
data class CreateHighlightDto(
    val bookmarkId: String,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val note: String? = null,
    val color: String? = null
)

@Serializable
data class UpdateHighlightDto(
    val note: String? = null,
    val color: String? = null
)
