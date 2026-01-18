package com.karakept.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Highlight(
    val id: String,
    val bookmarkId: String,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val note: String? = null,
    val color: String? = null,
    val createdAt: Long
)
