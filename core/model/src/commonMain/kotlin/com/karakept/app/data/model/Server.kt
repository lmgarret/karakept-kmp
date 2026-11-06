package com.karakept.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Server(
    val id: String, // Unique ID for local storage
    val url: String,
    val apiKey: String,
    val label: String
)
