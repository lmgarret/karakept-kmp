package com.karakept.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.compose.runtime.Immutable

@Immutable
@Entity(
    tableName = "highlights",
    indices = [Index(value = ["remoteId", "serverId"], unique = true), Index(value = ["bookmarkRemoteId"])]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String,
    val serverId: String,
    /** Remote string ID of the bookmark */
    val bookmarkRemoteId: String,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val note: String?,
    val color: String?,
    val createdAt: Long
)
