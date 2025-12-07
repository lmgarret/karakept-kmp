package com.karakept.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "saved_filters")
data class SavedFilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "📋", // Clipboard emoji as default
    val color: Long? = null, // Color as ARGB Long, null means default/primary
    val configJson: String,
    val isDefault: Boolean = false,
    val displayOrder: Int = 0,
    val isVisibleInDrawer: Boolean = true,
    val isQuickFilter: Boolean = false // True for system quick filters (All, Favorites, etc.)
)
