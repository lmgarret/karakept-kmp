package com.karakept.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable

@Immutable
@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String, // Asset ID from remote
    val bookmarkRemoteId: Long,
    val serverId: String,
    val assetType: String,
    val fileName: String?,
    val contentType: String?,
    val localPath: String? // Path to the downloaded file
)
