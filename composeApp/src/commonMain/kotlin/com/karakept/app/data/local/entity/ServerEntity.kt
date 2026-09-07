package com.karakept.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val url: String,
    val apiKey: String,
    val label: String
)
