package com.karakept.app.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class AssetDto(
    val id: String,
    val assetType: String,
    val fileName: String? = null,
    val contentType: String? = null
)
