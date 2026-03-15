package com.karakept.app.data.model

enum class ThumbnailSide {
    LEFT, RIGHT;

    companion object {
        fun fromString(value: String): ThumbnailSide =
            entries.find { it.name == value } ?: LEFT
    }
}
