package com.karakept.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Asset(
    val id: String,
    val assetType: String,
    val fileName: String?,
    val contentType: String? = null,
    val localPath: String? = null // For locally stored assets
)
