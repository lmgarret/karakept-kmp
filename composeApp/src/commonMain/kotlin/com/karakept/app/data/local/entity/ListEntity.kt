package com.karakept.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable

@Immutable
@Entity(
    tableName = "lists",
    indices = [Index(value = ["remoteId", "serverId"], unique = true)]
)
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String,
    val serverId: String,
    val name: String,
    val icon: String?,
    val parentId: String?,
    val isPublic: Boolean = false,
    val description: String?,
    val type: String = "manual",
    val query: String?,
    val updatedAt: Long
)
