package com.karakept.app.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateBookmarkDto(
    val type: String,
    val url: String
)
