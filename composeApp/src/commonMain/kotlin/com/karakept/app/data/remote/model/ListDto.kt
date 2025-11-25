package com.karakept.app.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class ListDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val icon: String,
    val parentId: String? = null,
    val type: String = "manual", // "manual" or "smart"
    val query: String? = null,
    val public: Boolean
)
