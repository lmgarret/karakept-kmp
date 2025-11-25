package com.karakept.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Long,
    val serverId: String,
    val url: String,
    val title: String,
    val content: String?,
    val imageUrl: String?,
    val description: String?,
    val createdAt: Long,
    val isArchived: Boolean,
    val isStarred: Boolean
)
