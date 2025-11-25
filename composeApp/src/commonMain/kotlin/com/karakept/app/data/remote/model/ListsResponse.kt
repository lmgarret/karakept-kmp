package com.karakept.app.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class ListsResponse(
    val lists: List<ListDto>
)
