package com.karakept.app.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class HighlightsResponse(
    val highlights: List<HighlightDto>
)
